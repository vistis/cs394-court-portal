package kh.edu.paragoniu.court_portal.participants;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import kh.edu.paragoniu.court_shared.entity.Participant;
import kh.edu.paragoniu.court_shared.repository.ParticipantRepository;
import kh.edu.paragoniu.court_shared.service.S3Service;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Powers the global Participants Directory: a filtered, paginated view over every
 * participant in the system, independent of any single case.
 *
 * <p>Uses JPQL via {@link EntityManager} to mirror the existing global-hearings-search
 * pattern in {@code hearings/HearingScheduleService} (no Specification support on
 * {@code ParticipantRepository}, and this feature is scoped to the portal module only).
 */
@Service
public class ParticipantDirectoryService {

    private static final String CACHE_NAME = "participantsDirectory";
    private static final String NO_PICTURE = "N/A";
    private static final String PARTY_INDIVIDUAL = "Individual";
    private static final String PARTY_GROUP = "Group";

    private final ParticipantRepository participantRepository;
    private final EntityManager entityManager;
    private final CacheManager cacheManager;
    private final ObjectMapper objectMapper;
    private final ObjectProvider<S3Service> s3ServiceProvider;

    public ParticipantDirectoryService(
        ParticipantRepository participantRepository,
        EntityManager entityManager,
        CacheManager cacheManager,
        ObjectMapper objectMapper,
        ObjectProvider<S3Service> s3ServiceProvider
    ) {
        this.participantRepository = participantRepository;
        this.entityManager = entityManager;
        this.cacheManager = cacheManager;
        this.objectMapper = objectMapper;
        this.s3ServiceProvider = s3ServiceProvider;
    }

    @Transactional(readOnly = true)
    @Cacheable(
        cacheNames = CACHE_NAME,
        key = "#q + '|' + #partyType + '|' + #pageable.pageNumber + '|' + #pageable.pageSize"
    )
    public Page<ParticipantDirectoryRow> search(
        String q,
        String partyType,
        Pageable pageable
    ) {
        List<String> conditions = new ArrayList<>();
        Map<String, Object> params = new LinkedHashMap<>();

        if (q != null && !q.isBlank()) {
            conditions.add(
                "(LOWER(p.name) LIKE :q OR EXISTS (" +
                "SELECT 1 FROM CaseParticipant cp " +
                "WHERE cp.participantEntity = p AND LOWER(cp.caseEntity.caseNumber) LIKE :q))"
            );
            params.put("q", "%" + q.trim().toLowerCase() + "%");
        }
        if (partyType != null && !partyType.isBlank()) {
            conditions.add("LOWER(p.partyType) = :partyType");
            params.put("partyType", partyType.toLowerCase());
        }

        String whereClause = conditions.isEmpty()
            ? ""
            : " WHERE " + String.join(" AND ", conditions);

        TypedQuery<Long> countQuery = entityManager.createQuery(
            "SELECT COUNT(p) FROM Participant p" + whereClause,
            Long.class
        );
        params.forEach(countQuery::setParameter);
        long total = countQuery.getSingleResult();

        TypedQuery<Participant> dataQuery = entityManager.createQuery(
            "SELECT p FROM Participant p" + whereClause + " ORDER BY p.name",
            Participant.class
        );
        params.forEach(dataQuery::setParameter);
        dataQuery.setFirstResult((int) pageable.getOffset());
        dataQuery.setMaxResults(pageable.getPageSize());

        List<ParticipantDirectoryRow> rows = dataQuery
            .getResultList()
            .stream()
            .map(this::toRow)
            .toList();

        return new PageImpl<>(rows, pageable, total);
    }

    public List<String> findPartyTypeOptions() {
        return List.of(PARTY_INDIVIDUAL, PARTY_GROUP);
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CACHE_NAME, key = "'profile:' + #participantId")
    public ParticipantProfileView findProfile(UUID participantId) {
        Participant participant = participantRepository
            .findById(participantId)
            .orElseThrow(() -> new ParticipantNotFoundException(participantId));

        return new ParticipantProfileView(
            participant.getParticipantId(),
            participant.getName(),
            initials(participant.getName()),
            participant.getPartyType(),
            badgeClass(participant.getPartyType()),
            nameLabel(participant.getPartyType()),
            contactField(participant, "email"),
            contactField(participant, "phone"),
            resolveProfileImageUrl(participant.getProfilePicturePath())
        );
    }

    @Transactional
    public UUID createParticipant(CreateParticipantForm form) {
        String partyType = validatePartyType(form.getPartyType());
        String name = trim(form.getName());
        if (name == null) {
            throw new ParticipantDirectoryException("name", "Name is required.");
        }

        ObjectNode contactInfo = objectMapper.createObjectNode();
        contactInfo.put("email", trim(form.getEmail()));
        contactInfo.put("phone", trim(form.getPhone()));

        Participant participant = new Participant();
        participant.setPartyType(partyType);
        participant.setName(name);
        participant.setContactInfo(contactInfo);
        participant.setProfilePicturePath(resolveProfilePicture(form.getProfileImage()));

        Participant saved = participantRepository.save(participant);
        scheduleCacheEvictionAfterCommit();
        return saved.getParticipantId();
    }

    /**
     * Registers a post-commit callback rather than annotating this method with
     * @CacheEvict: @Transactional and cache-eviction advice stacked on the same
     * method have no guaranteed ordering, so eviction could otherwise fire before
     * the INSERT commits and let a concurrent read repopulate the cache with
     * stale (pre-commit) data.
     */
    private void scheduleCacheEvictionAfterCommit() {
        TransactionSynchronizationManager.registerSynchronization(
            new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    Cache cache = cacheManager.getCache(CACHE_NAME);
                    if (cache != null) {
                        cache.clear();
                    }
                }
            }
        );
    }

    private String resolveProfilePicture(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return NO_PICTURE;
        }
        S3Service s3Service = s3ServiceProvider.getIfAvailable();
        if (s3Service == null) {
            return NO_PICTURE;
        }
        try {
            return s3Service.uploadFile(
                "participants",
                Objects.requireNonNull(file.getOriginalFilename()),
                file.getBytes(),
                file.getContentType()
            );
        } catch (IOException | RuntimeException ex) {
            throw new ParticipantDirectoryException(
                "profileImage",
                "The photo could not be uploaded. Please try again."
            );
        }
    }

    private ParticipantDirectoryRow toRow(Participant participant) {
        long involvedCases = entityManager
            .createQuery(
                "SELECT COUNT(cp) FROM CaseParticipant cp " +
                "WHERE cp.participantEntity.participantId = :participantId",
                Long.class
            )
            .setParameter("participantId", participant.getParticipantId())
            .getSingleResult();
        return new ParticipantDirectoryRow(
            participant.getParticipantId(),
            participant.getName(),
            participant.getPartyType(),
            badgeClass(participant.getPartyType()),
            contactField(participant, "email"),
            involvedCases
        );
    }

    private String contactField(Participant participant, String field) {
        var contactInfo = participant.getContactInfo();
        if (contactInfo == null || !contactInfo.has(field)) {
            return "N/A";
        }
        String value = contactInfo.get(field).stringValue();
        return value == null || value.isBlank() ? "N/A" : value;
    }

    private String initials(String name) {
        if (name == null || name.isBlank()) {
            return "?";
        }
        String[] parts = name.trim().split("\\s+");
        String first = parts[0].substring(0, 1);
        String second = parts.length > 1
            ? parts[parts.length - 1].substring(0, 1)
            : "";
        return (first + second).toUpperCase(Locale.ENGLISH);
    }

    private String nameLabel(String partyType) {
        return PARTY_GROUP.equalsIgnoreCase(partyType)
            ? "Organization / Group Name"
            : "Full Legal Name";
    }

    private String resolveProfileImageUrl(String profilePicturePath) {
        if (
            profilePicturePath == null ||
            profilePicturePath.isBlank() ||
            NO_PICTURE.equalsIgnoreCase(profilePicturePath)
        ) {
            return null;
        }
        S3Service s3Service = s3ServiceProvider.getIfAvailable();
        return s3Service == null
            ? null
            : s3Service.generatePublicUrl(profilePicturePath);
    }

    private String badgeClass(String partyType) {
        return PARTY_GROUP.equalsIgnoreCase(partyType) ? "badge--green" : "badge--blue";
    }

    private String validatePartyType(String partyType) {
        if (partyType == null || partyType.isBlank()) {
            throw new ParticipantDirectoryException(
                "partyType",
                "Party type is required."
            );
        }
        if (PARTY_INDIVIDUAL.equalsIgnoreCase(partyType)) {
            return PARTY_INDIVIDUAL;
        }
        if (PARTY_GROUP.equalsIgnoreCase(partyType)) {
            return PARTY_GROUP;
        }
        throw new ParticipantDirectoryException(
            "partyType",
            "Party type must be Individual or Group."
        );
    }

    private String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
