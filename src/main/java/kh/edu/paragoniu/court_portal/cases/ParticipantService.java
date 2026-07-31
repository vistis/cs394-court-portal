package kh.edu.paragoniu.court_portal.cases;

import jakarta.persistence.EntityManager;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import kh.edu.paragoniu.court_shared.entity.Case;
import kh.edu.paragoniu.court_shared.entity.CaseParticipant;
import kh.edu.paragoniu.court_shared.entity.CaseParticipantId;
import kh.edu.paragoniu.court_shared.entity.Participant;
import kh.edu.paragoniu.court_shared.entity.ParticipantRole;
import kh.edu.paragoniu.court_shared.repository.CaseParticipantRepository;
import kh.edu.paragoniu.court_shared.repository.CaseRepository;
import kh.edu.paragoniu.court_shared.repository.ParticipantRepository;
import kh.edu.paragoniu.court_shared.repository.ParticipantRoleRepository;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.JsonNode;

@Service
public class ParticipantService {

    private static final String PARTY_INDIVIDUAL = "Individual";
    private static final String PARTY_GROUP = "Group";

    /** Shared with participants/ParticipantDirectoryService's Involved Cases cache. */
    private static final String PARTICIPANTS_DIRECTORY_CACHE = "participantsDirectory";

    private final CaseRepository caseRepository;
    private final CaseParticipantRepository caseParticipantRepository;
    private final ParticipantRepository participantRepository;
    private final ParticipantRoleRepository participantRoleRepository;
    private final CacheManager cacheManager;
    private final EntityManager entityManager;

    /** Cap on the participant-picker list; it is a searchable modal, not a full dump. */
    private static final int PARTICIPANT_OPTION_LIMIT = 50;

    public ParticipantService(
        CaseRepository caseRepository,
        CaseParticipantRepository caseParticipantRepository,
        ParticipantRepository participantRepository,
        ParticipantRoleRepository participantRoleRepository,
        CacheManager cacheManager,
        EntityManager entityManager
    ) {
        this.caseRepository = caseRepository;
        this.caseParticipantRepository = caseParticipantRepository;
        this.participantRepository = participantRepository;
        this.participantRoleRepository = participantRoleRepository;
        this.cacheManager = cacheManager;
        this.entityManager = entityManager;
    }

    @Transactional(readOnly = true)
    @Cacheable(
        cacheNames = "caseParticipants",
        key = "#caseId + '|' + #query + '|' + #roleId"
    )
    public List<CaseParticipantRow> findCaseParticipants(
        UUID caseId,
        String query,
        Integer roleId
    ) {
        ensureCaseExists(caseId);
        String normalizedQuery = normalize(query);
        return caseParticipantRepository
            .findByIdCaseId(caseId)
            .stream()
            .filter(row ->
                roleId == null ||
                roleId.equals(row.getParticipantRole().getRoleId())
            )
            .filter(row -> matchesQuery(row, normalizedQuery))
            .sorted(
                Comparator.comparing(row ->
                    row.getParticipantEntity().getName().toLowerCase(Locale.ENGLISH)
                )
            )
            .map(this::toRow)
            .toList();
    }

    @Cacheable(value = "refData", key = "'participantRoleOptions'")
    @Transactional(readOnly = true)
    public List<FilterOption> findRoleOptions() {
        return participantRoleRepository
            .findAll(Sort.by("roleName"))
            .stream()
            .map(role -> new FilterOption(role.getRoleId(), role.getRoleName()))
            .toList();
    }

    @Transactional(readOnly = true)
    public List<ParticipantOption> findParticipantOptions(
        UUID caseId,
        String partyType,
        String query
    ) {
        ensureCaseExists(caseId);
        String normalizedPartyType = validatePartyType(partyType);
        String normalizedQuery = normalize(query);
        Set<UUID> assignedIds = caseParticipantRepository
            .findByIdCaseId(caseId)
            .stream()
            .map(row -> row.getParticipantEntity().getParticipantId())
            .collect(Collectors.toSet());

        // Filter by party type (and name, when searching) in the database rather
        // than loading every participant row into memory. The already-assigned
        // parties are excluded afterwards (that set is small per case), so pull a
        // few extra rows to still fill the capped list.
        boolean hasQuery = !normalizedQuery.isBlank();
        String jpql =
            "SELECT p FROM Participant p WHERE LOWER(p.partyType) = LOWER(:partyType)";
        if (hasQuery) {
            jpql += " AND LOWER(p.name) LIKE :q";
        }
        jpql += " ORDER BY p.name";

        var typedQuery = entityManager
            .createQuery(jpql, Participant.class)
            .setParameter("partyType", normalizedPartyType);
        if (hasQuery) {
            typedQuery.setParameter("q", "%" + normalizedQuery + "%");
        }
        typedQuery.setMaxResults(PARTICIPANT_OPTION_LIMIT + assignedIds.size());

        return typedQuery
            .getResultList()
            .stream()
            .filter(participant ->
                !assignedIds.contains(participant.getParticipantId())
            )
            .limit(PARTICIPANT_OPTION_LIMIT)
            .map(participant ->
                new ParticipantOption(
                    participant.getParticipantId(),
                    participant.getName(),
                    formatContactInfo(participant.getContactInfo())
                )
            )
            .toList();
    }

    @Transactional
    public void addParticipant(UUID caseId, AddCaseParticipantForm form) {
        Case caseEntity = caseRepository
            .findById(caseId)
            .orElseThrow(() -> new CaseDetailNotFoundException(caseId));
        String partyType = validatePartyType(form.getPartyType());

        Participant participant = participantRepository
            .findById(form.getParticipantId())
            .orElseThrow(() ->
                new CaseParticipantException(
                    "participantId",
                    "Selected participant does not exist."
                )
            );
        if (!partyType.equalsIgnoreCase(participant.getPartyType())) {
            throw new CaseParticipantException(
                "participantId",
                "Selected participant does not match the selected party type."
            );
        }

        ParticipantRole role = participantRoleRepository
            .findById(form.getRoleId())
            .orElseThrow(() ->
                new CaseParticipantException(
                    "roleId",
                    "Selected participant role does not exist."
                )
            );
        CaseParticipantId id = new CaseParticipantId(
            caseId,
            participant.getParticipantId()
        );
        if (caseParticipantRepository.existsById(id)) {
            throw new CaseParticipantException(
                "participantId",
                "This participant is already assigned to the case."
            );
        }

        CaseParticipant caseParticipant = new CaseParticipant();
        caseParticipant.setId(id);
        caseParticipant.setCaseEntity(caseEntity);
        caseParticipant.setParticipantEntity(participant);
        caseParticipant.setParticipantRole(role);
        caseParticipantRepository.save(caseParticipant);
        scheduleCaseParticipantCacheEvictionAfterCommit(caseId);
    }

    @Transactional
    public void removeParticipant(UUID caseId, UUID participantId) {
        ensureCaseExists(caseId);
        CaseParticipantId id = new CaseParticipantId(caseId, participantId);
        if (!caseParticipantRepository.existsById(id)) {
            throw new CaseParticipantException(
                null,
                "This participant is not assigned to the case."
            );
        }
        caseParticipantRepository.deleteById(id);
        scheduleCaseParticipantCacheEvictionAfterCommit(caseId);
    }

    /**
     * Evicts every cache that case_participants changes affect, after this
     * transaction commits: this case's own caseParticipants list, the public
     * case-detail view, and participants/ParticipantDirectoryService's cache
     * (its involved-cases count and the profile's Involved Cases tab).
     * Registered manually rather than via @CacheEvict: stacking @CacheEvict
     * with @Transactional on one method has no ordering guarantee and can
     * evict before commit, letting a concurrent read repopulate the cache
     * with stale (pre-commit) data.
     */
    private void scheduleCaseParticipantCacheEvictionAfterCommit(UUID caseId) {
        TransactionSynchronizationManager.registerSynchronization(
            new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    Cache caseParticipantsCache = cacheManager.getCache(
                        "caseParticipants"
                    );
                    if (caseParticipantsCache != null) {
                        caseParticipantsCache.clear();
                    }
                    Cache publicCaseDetailCache = cacheManager.getCache(
                        "publicCaseDetail"
                    );
                    if (publicCaseDetailCache != null) {
                        publicCaseDetailCache.evict(caseId);
                    }
                    Cache directoryCache = cacheManager.getCache(
                        PARTICIPANTS_DIRECTORY_CACHE
                    );
                    if (directoryCache != null) {
                        directoryCache.clear();
                    }
                }
            }
        );
    }

    private CaseParticipantRow toRow(CaseParticipant caseParticipant) {
        Participant participant = caseParticipant.getParticipantEntity();
        return new CaseParticipantRow(
            participant.getParticipantId(),
            participant.getName(),
            participant.getPartyType(),
            caseParticipant.getParticipantRole().getRoleName(),
            formatContactInfo(participant.getContactInfo())
        );
    }

    private boolean matchesQuery(CaseParticipant row, String normalizedQuery) {
        if (normalizedQuery.isBlank()) {
            return true;
        }
        return (
            row
                .getParticipantEntity()
                .getName()
                .toLowerCase(Locale.ENGLISH)
                .contains(normalizedQuery) ||
            row
                .getParticipantRole()
                .getRoleName()
                .toLowerCase(Locale.ENGLISH)
                .contains(normalizedQuery)
        );
    }

    private String formatContactInfo(JsonNode contactInfo) {
        if (contactInfo == null || contactInfo.toString().isBlank()) {
            return "N/A";
        }
        String value = contactInfo.toString();
        if ("null".equalsIgnoreCase(value) || "{}".equals(value)) {
            return "N/A";
        }
        return value
            .replace("{", "")
            .replace("}", "")
            .replace("\"", "")
            .replace(",", ", ")
            .replace(":", ": ");
    }

    private void ensureCaseExists(UUID caseId) {
        if (!caseRepository.existsById(caseId)) {
            throw new CaseDetailNotFoundException(caseId);
        }
    }

    private String validatePartyType(String partyType) {
        if (partyType == null || partyType.isBlank()) {
            throw new CaseParticipantException("partyType", "Party type is required.");
        }
        if (PARTY_INDIVIDUAL.equalsIgnoreCase(partyType)) {
            return PARTY_INDIVIDUAL;
        }
        if (PARTY_GROUP.equalsIgnoreCase(partyType)) {
            return PARTY_GROUP;
        }
        throw new CaseParticipantException(
            "partyType",
            "Party type must be Individual or Group."
        );
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ENGLISH);
    }
}
