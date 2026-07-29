package kh.edu.paragoniu.court_portal.legal;

import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.persistence.TypedQuery;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import kh.edu.paragoniu.court_portal.cases.AssignPersonOption;
import kh.edu.paragoniu.court_portal.cases.CaseDetailNotFoundException;
import kh.edu.paragoniu.court_shared.entity.Case;
import kh.edu.paragoniu.court_shared.entity.CaseJudge;
import kh.edu.paragoniu.court_shared.entity.Judge;
import kh.edu.paragoniu.court_shared.entity.Lawyer;
import kh.edu.paragoniu.court_shared.entity.LegalRepresentation;
import kh.edu.paragoniu.court_shared.entity.LegalRepresentationId;
import kh.edu.paragoniu.court_shared.repository.CaseJudgeRepository;
import kh.edu.paragoniu.court_shared.repository.JudgeRepository;
import kh.edu.paragoniu.court_shared.repository.LawyerRepository;
import kh.edu.paragoniu.court_shared.repository.LegalRepresentationRepository;
import kh.edu.paragoniu.court_shared.service.S3Service;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Powers the Lawyers &amp; Judges directory: two searchable, paginated lists
 * (lawyers and judges) with each person's active-case count. Active cases come
 * from {@code legal_representations} for lawyers and {@code case_judges} for
 * judges. JPQL via {@link EntityManager}, mirroring the other directory
 * services. All reads are Redis-cached (cache-aside).
 */
@Service
@RequiredArgsConstructor
public class LawyerJudgeService {

    private static final String NO_PICTURE = "N/A";

    private final EntityManager entityManager;
    private final LawyerRepository lawyerRepository;
    private final JudgeRepository judgeRepository;
    private final CaseJudgeRepository caseJudgeRepository;
    private final LegalRepresentationRepository legalRepresentationRepository;
    private final ObjectProvider<S3Service> s3ServiceProvider;

    @Cacheable("lawyerList")
    @Transactional(readOnly = true)
    public Page<LawyerRow> searchLawyers(String query, Pageable pageable) {
        List<String> conditions = new ArrayList<>();
        Map<String, Object> params = new LinkedHashMap<>();
        applyNameOrLicenseFilter(query, conditions, params, "l");

        String whereClause = conditions.isEmpty()
            ? ""
            : " WHERE " + String.join(" AND ", conditions);

        TypedQuery<Long> countQuery = entityManager.createQuery(
            "SELECT COUNT(l) FROM Lawyer l" + whereClause,
            Long.class
        );
        params.forEach(countQuery::setParameter);
        long total = countQuery.getSingleResult();

        TypedQuery<LawyerProjection> dataQuery = entityManager.createQuery(
            "SELECT new kh.edu.paragoniu.court_portal.legal.LawyerProjection(" +
            "l.lawyerId, l.firstName, l.lastName, l.licenseNumber, l.firmName, " +
            "(SELECT COUNT(DISTINCT lr.caseEntity.caseId) FROM LegalRepresentation lr " +
            "WHERE lr.lawyerEntity.lawyerId = l.lawyerId)) " +
            "FROM Lawyer l" +
            whereClause +
            " ORDER BY l.lastName, l.firstName",
            LawyerProjection.class
        );
        params.forEach(dataQuery::setParameter);
        dataQuery.setFirstResult((int) pageable.getOffset());
        dataQuery.setMaxResults(pageable.getPageSize());

        List<LawyerRow> rows = dataQuery
            .getResultList()
            .stream()
            .map(this::toLawyerRow)
            .toList();

        return new PageImpl<>(rows, pageable, total);
    }

    @Cacheable("judgeList")
    @Transactional(readOnly = true)
    public Page<JudgeRow> searchJudges(String query, String status, Pageable pageable) {
        List<String> conditions = new ArrayList<>();
        Map<String, Object> params = new LinkedHashMap<>();
        applyNameOrLicenseFilter(query, conditions, params, "j");

        if ("active".equalsIgnoreCase(status)) {
            conditions.add("j.isActive = true");
        } else if ("inactive".equalsIgnoreCase(status)) {
            conditions.add("j.isActive = false");
        }

        String whereClause = conditions.isEmpty()
            ? ""
            : " WHERE " + String.join(" AND ", conditions);

        TypedQuery<Long> countQuery = entityManager.createQuery(
            "SELECT COUNT(j) FROM Judge j" + whereClause,
            Long.class
        );
        params.forEach(countQuery::setParameter);
        long total = countQuery.getSingleResult();

        TypedQuery<JudgeProjection> dataQuery = entityManager.createQuery(
            "SELECT new kh.edu.paragoniu.court_portal.legal.JudgeProjection(" +
            "j.judgeId, j.firstName, j.lastName, j.isActive, " +
            "(SELECT COUNT(DISTINCT cj.caseEntity.caseId) FROM CaseJudge cj " +
            "WHERE cj.judgeEntity.judgeId = j.judgeId)) " +
            "FROM Judge j" +
            whereClause +
            " ORDER BY j.lastName, j.firstName",
            JudgeProjection.class
        );
        params.forEach(dataQuery::setParameter);
        dataQuery.setFirstResult((int) pageable.getOffset());
        dataQuery.setMaxResults(pageable.getPageSize());

        List<JudgeRow> rows = dataQuery
            .getResultList()
            .stream()
            .map(this::toJudgeRow)
            .toList();

        return new PageImpl<>(rows, pageable, total);
    }

    /** Matches the free-text search against name (first/last/full) or license. */
    private void applyNameOrLicenseFilter(
        String query,
        List<String> conditions,
        Map<String, Object> params,
        String alias
    ) {
        if (query == null || query.isBlank()) {
            return;
        }
        conditions.add(
            "(LOWER(" + alias + ".firstName) LIKE :q " +
            "OR LOWER(" + alias + ".lastName) LIKE :q " +
            "OR LOWER(CONCAT(" + alias + ".firstName, ' ', " + alias + ".lastName)) LIKE :q " +
            "OR LOWER(" + alias + ".licenseNumber) LIKE :q)"
        );
        params.put("q", "%" + query.toLowerCase(Locale.ENGLISH).trim() + "%");
    }

    private LawyerRow toLawyerRow(LawyerProjection p) {
        return new LawyerRow(
            p.lawyerId().toString(),
            fullName(p.firstName(), p.lastName()),
            p.licenseNumber(),
            p.firmName() == null || p.firmName().isBlank() ? "—" : p.firmName(),
            p.activeCases() == null ? 0L : p.activeCases()
        );
    }

    private JudgeRow toJudgeRow(JudgeProjection p) {
        return new JudgeRow(
            p.judgeId().toString(),
            "Hon. " + fullName(p.firstName(), p.lastName()),
            // No chambers/courtroom field exists in the fixed schema.
            "N/A",
            p.active() ? "Active" : "Inactive / Retired",
            p.active() ? "badge--green" : "badge--gray",
            p.activeCases() == null ? 0L : p.activeCases()
        );
    }

    /**
     * Display name of a judge (with the {@code Hon.} honorific) for the assign
     * modal / confirmation. Throws if the id is not an actual judge so the
     * controller can render a 404.
     */
    @Transactional(readOnly = true)
    public String findJudgeName(UUID judgeId) {
        try {
            return "Hon. " + entityManager
                .createQuery(
                    "SELECT CONCAT(j.firstName, ' ', j.lastName) " +
                    "FROM Judge j WHERE j.judgeId = :id",
                    String.class
                )
                .setParameter("id", judgeId)
                .getSingleResult();
        } catch (NoResultException ex) {
            throw new CaseDetailNotFoundException(judgeId);
        }
    }

    /**
     * Case suggestions for the "Assign Case to Judge" modal. Cases this judge is
     * already on are flagged (not hidden) so the chief can see them but can't
     * double-assign. Not cached — it changes per keystroke.
     */
    @Transactional(readOnly = true)
    public List<JudgeAssignCaseOption> searchAssignableCasesForJudge(
        UUID judgeId,
        String query,
        int limit
    ) {
        Set<UUID> assigned = new HashSet<>(
            entityManager
                .createQuery(
                    "SELECT cj.caseEntity.caseId FROM CaseJudge cj " +
                    "WHERE cj.judgeEntity.judgeId = :jid",
                    UUID.class
                )
                .setParameter("jid", judgeId)
                .getResultList()
        );

        boolean hasQuery = query != null && !query.isBlank();
        String jpql =
            "SELECT new kh.edu.paragoniu.court_portal.legal.JudgeAssignCaseProjection(" +
            "c.caseId, c.caseNumber, c.title, c.status.name) FROM Case c";
        if (hasQuery) {
            jpql += " WHERE LOWER(c.caseNumber) LIKE :q OR LOWER(c.title) LIKE :q";
        }
        jpql += " ORDER BY c.caseNumber";

        TypedQuery<JudgeAssignCaseProjection> q = entityManager.createQuery(
            jpql,
            JudgeAssignCaseProjection.class
        );
        if (hasQuery) {
            q.setParameter("q", "%" + query.toLowerCase(Locale.ENGLISH).trim() + "%");
        }
        q.setMaxResults(limit);

        return q
            .getResultList()
            .stream()
            .map(p ->
                new JudgeAssignCaseOption(
                    p.caseId().toString(),
                    p.caseNumber(),
                    p.title(),
                    prettyStatus(p.statusName()),
                    statusBadgeClass(p.statusName()),
                    assigned.contains(p.caseId())
                )
            )
            .toList();
    }

    /**
     * Assign an existing case to a judge, either as the presiding judge or as an
     * associate ({@code is_presiding}). Evicts the judge list (active-case count)
     * and the judge's involved-cases cache. Returns the case number for the
     * confirmation message.
     */
    /**
     * Judge suggestions for the quick-assign popup on the Case Detail page —
     * the reverse of {@link #searchAssignableCasesForJudge}: given a case, find
     * judges to add. Only active judges are offered; judges already on the case
     * are flagged (not hidden). Not cached — it changes per keystroke.
     */
    @Transactional(readOnly = true)
    public List<AssignPersonOption> searchAssignableJudgesForCase(
        UUID caseId,
        String query,
        int limit
    ) {
        Set<UUID> assigned = new HashSet<>(
            entityManager
                .createQuery(
                    "SELECT cj.judgeEntity.judgeId FROM CaseJudge cj " +
                    "WHERE cj.caseEntity.caseId = :cid",
                    UUID.class
                )
                .setParameter("cid", caseId)
                .getResultList()
        );

        boolean hasQuery = query != null && !query.isBlank();
        String jpql = "SELECT j FROM Judge j WHERE j.isActive = true";
        if (hasQuery) {
            jpql +=
                " AND (LOWER(j.firstName) LIKE :q OR LOWER(j.lastName) LIKE :q " +
                "OR LOWER(CONCAT(j.firstName, ' ', j.lastName)) LIKE :q " +
                "OR LOWER(j.licenseNumber) LIKE :q)";
        }
        jpql += " ORDER BY j.lastName, j.firstName";

        TypedQuery<Judge> q = entityManager.createQuery(jpql, Judge.class);
        if (hasQuery) {
            q.setParameter("q", "%" + query.toLowerCase(Locale.ENGLISH).trim() + "%");
        }
        q.setMaxResults(limit);

        return q
            .getResultList()
            .stream()
            .map(j ->
                new AssignPersonOption(
                    j.getJudgeId().toString(),
                    "Hon. " + fullName(j.getFirstName(), j.getLastName()),
                    j.getLicenseNumber() == null || j.getLicenseNumber().isBlank()
                        ? "Judge"
                        : j.getLicenseNumber(),
                    assigned.contains(j.getJudgeId())
                )
            )
            .toList();
    }

    @Caching(evict = {
        @CacheEvict(value = "judgeList", allEntries = true),
        @CacheEvict(value = "judgeCases", allEntries = true),
        @CacheEvict(value = "caseList", allEntries = true),
        @CacheEvict(value = "caseDetail", key = "#caseId"),
        @CacheEvict(value = "publicCases", allEntries = true),
        @CacheEvict(value = "publicCaseDetail", key = "#caseId")
    })
    @Transactional
    public String assignCaseToJudge(UUID judgeId, UUID caseId, boolean presiding) {
        String judgeName = entityManager
            .createQuery(
                "SELECT CONCAT(j.firstName, ' ', j.lastName) " +
                "FROM Judge j WHERE j.judgeId = :id",
                String.class
            )
            .setParameter("id", judgeId)
            .getResultStream()
            .findFirst()
            .orElseThrow(() -> new CaseDetailNotFoundException(judgeId));

        String caseNumber = entityManager
            .createQuery(
                "SELECT c.caseNumber FROM Case c WHERE c.caseId = :id",
                String.class
            )
            .setParameter("id", caseId)
            .getResultStream()
            .findFirst()
            .orElseThrow(() -> new CaseDetailNotFoundException(caseId));

        long existing = entityManager
            .createQuery(
                "SELECT COUNT(cj) FROM CaseJudge cj " +
                "WHERE cj.caseEntity.caseId = :caseId " +
                "AND cj.judgeEntity.judgeId = :judgeId",
                Long.class
            )
            .setParameter("caseId", caseId)
            .setParameter("judgeId", judgeId)
            .getSingleResult();
        if (existing > 0) {
            throw new IllegalStateException(
                "Case " + caseNumber + " is already assigned to " + judgeName + "."
            );
        }

        CaseJudge assignment = new CaseJudge();
        assignment.setCaseEntity(entityManager.getReference(Case.class, caseId));
        assignment.setJudgeEntity(entityManager.getReference(Judge.class, judgeId));
        assignment.setPresiding(presiding);
        assignment.setAssignedAt(Instant.now());
        caseJudgeRepository.save(assignment);

        return caseNumber;
    }

    /**
     * Case suggestions for the "Assign Case to Lawyer" modal (step 1 of 2 —
     * picking a case). Not cached — it changes per keystroke.
     */
    @Transactional(readOnly = true)
    public List<LawyerAssignCaseOption> searchAssignableCasesForLawyer(
        String query,
        int limit
    ) {
        boolean hasQuery = query != null && !query.isBlank();
        String jpql =
            "SELECT new kh.edu.paragoniu.court_portal.legal.LawyerAssignCaseProjection(" +
            "c.caseId, c.caseNumber, c.title, c.status.name) FROM Case c";
        if (hasQuery) {
            jpql += " WHERE LOWER(c.caseNumber) LIKE :q OR LOWER(c.title) LIKE :q";
        }
        jpql += " ORDER BY c.caseNumber";

        TypedQuery<LawyerAssignCaseProjection> q = entityManager.createQuery(
            jpql,
            LawyerAssignCaseProjection.class
        );
        if (hasQuery) {
            q.setParameter("q", "%" + query.toLowerCase(Locale.ENGLISH).trim() + "%");
        }
        q.setMaxResults(limit);

        return q
            .getResultList()
            .stream()
            .map(p ->
                new LawyerAssignCaseOption(
                    p.caseId().toString(),
                    p.caseNumber(),
                    p.title(),
                    prettyStatus(p.statusName()),
                    statusBadgeClass(p.statusName())
                )
            )
            .toList();
    }

    /**
     * The parties (participants) on a case — step 2 of the Assign Case to Lawyer
     * modal, since a legal representation is tied to a specific party. Parties
     * this lawyer already represents on the case are flagged so they can't be
     * assigned twice.
     */
    @Transactional(readOnly = true)
    public List<CaseParticipantOption> findAssignableParticipants(
        UUID lawyerId,
        UUID caseId
    ) {
        Set<UUID> represented = new HashSet<>(
            entityManager
                .createQuery(
                    "SELECT lr.participantEntity.participantId FROM LegalRepresentation lr " +
                    "WHERE lr.caseEntity.caseId = :caseId " +
                    "AND lr.lawyerEntity.lawyerId = :lawyerId",
                    UUID.class
                )
                .setParameter("caseId", caseId)
                .setParameter("lawyerId", lawyerId)
                .getResultList()
        );

        return entityManager
            .createQuery(
                "SELECT new kh.edu.paragoniu.court_portal.legal.CaseParticipantProjection(" +
                "cp.participantEntity.participantId, cp.participantEntity.name, " +
                "cp.participantEntity.partyType, cp.participantRole.roleName) " +
                "FROM CaseParticipant cp WHERE cp.caseEntity.caseId = :caseId " +
                "ORDER BY cp.participantEntity.name",
                CaseParticipantProjection.class
            )
            .setParameter("caseId", caseId)
            .getResultList()
            .stream()
            .map(p ->
                new CaseParticipantOption(
                    p.participantId().toString(),
                    p.name(),
                    prettyStatus(p.partyType()),
                    p.roleName(),
                    represented.contains(p.participantId())
                )
            )
            .toList();
    }

    /**
     * Assign a case to a lawyer as the counsel for a specific party. Inserts a
     * {@code legal_representations} row (case + participant + lawyer). Validates
     * that the party actually belongs to the case and isn't already represented
     * by this lawyer. Evicts the lawyer list (active-case count) and the
     * involved-cases cache. Returns the case number for the confirmation message.
     */
    @Caching(evict = {
        @CacheEvict(value = "lawyerList", allEntries = true),
        @CacheEvict(value = "lawyerCases", allEntries = true)
    })
    @Transactional
    public String assignCaseToLawyer(UUID lawyerId, UUID caseId, UUID participantId) {
        entityManager
            .createQuery(
                "SELECT l.lawyerId FROM Lawyer l WHERE l.lawyerId = :id",
                UUID.class
            )
            .setParameter("id", lawyerId)
            .getResultStream()
            .findFirst()
            .orElseThrow(() -> new CaseDetailNotFoundException(lawyerId));

        String caseNumber = entityManager
            .createQuery(
                "SELECT c.caseNumber FROM Case c WHERE c.caseId = :id",
                String.class
            )
            .setParameter("id", caseId)
            .getResultStream()
            .findFirst()
            .orElseThrow(() -> new CaseDetailNotFoundException(caseId));

        // The party must actually be on this case.
        long onCase = entityManager
            .createQuery(
                "SELECT COUNT(cp) FROM CaseParticipant cp " +
                "WHERE cp.caseEntity.caseId = :caseId " +
                "AND cp.participantEntity.participantId = :participantId",
                Long.class
            )
            .setParameter("caseId", caseId)
            .setParameter("participantId", participantId)
            .getSingleResult();
        if (onCase == 0) {
            throw new IllegalStateException(
                "That party is not on case " + caseNumber + "."
            );
        }

        long existing = entityManager
            .createQuery(
                "SELECT COUNT(lr) FROM LegalRepresentation lr " +
                "WHERE lr.caseEntity.caseId = :caseId " +
                "AND lr.participantEntity.participantId = :participantId " +
                "AND lr.lawyerEntity.lawyerId = :lawyerId",
                Long.class
            )
            .setParameter("caseId", caseId)
            .setParameter("participantId", participantId)
            .setParameter("lawyerId", lawyerId)
            .getSingleResult();
        if (existing > 0) {
            throw new IllegalStateException(
                "This lawyer already represents that party on case " + caseNumber + "."
            );
        }

        LegalRepresentation representation = new LegalRepresentation();
        // No @MapsId on this entity — the join columns are read-only, so the
        // composite id carries the actual case/participant/lawyer values.
        representation.setId(
            new LegalRepresentationId(caseId, participantId, lawyerId)
        );
        legalRepresentationRepository.save(representation);

        return caseNumber;
    }

    /**
     * Header/information cards for the Lawyer Profile page. Throws if the id is
     * not a real lawyer so the controller can render a 404.
     */
    @Cacheable("lawyerDetail")
    @Transactional(readOnly = true)
    public LawyerProfile findLawyerProfile(UUID lawyerId) {
        LawyerProfileProjection p;
        try {
            p = entityManager
                .createQuery(
                    "SELECT new kh.edu.paragoniu.court_portal.legal.LawyerProfileProjection(" +
                    "l.lawyerId, l.firstName, l.lastName, l.licenseNumber, " +
                    "l.firmName, l.profilePicturePath) " +
                    "FROM Lawyer l WHERE l.lawyerId = :id",
                    LawyerProfileProjection.class
                )
                .setParameter("id", lawyerId)
                .getSingleResult();
        } catch (NoResultException ex) {
            throw new CaseDetailNotFoundException(lawyerId);
        }

        String name = fullName(p.firstName(), p.lastName());
        String path = p.profilePicturePath();
        String imageUrl = path != null && path.startsWith("http") ? path : null;
        return new LawyerProfile(
            p.lawyerId().toString(),
            name,
            initials(name),
            p.firstName(),
            p.lastName(),
            p.licenseNumber(),
            p.firmName() == null || p.firmName().isBlank() ? "—" : p.firmName(),
            imageUrl
        );
    }

    /** The cases a lawyer is involved in (via the parties they represent). */
    @Cacheable("lawyerCases")
    @Transactional(readOnly = true)
    public List<InvolvedCaseRow> findInvolvedCases(UUID lawyerId) {
        return entityManager
            .createQuery(
                "SELECT new kh.edu.paragoniu.court_portal.legal.InvolvedCaseProjection(" +
                "lr.caseEntity.caseId, lr.caseEntity.caseNumber, lr.caseEntity.title, " +
                "lr.participantEntity.name, lr.participantEntity.partyType, " +
                "lr.caseEntity.status.name) " +
                "FROM LegalRepresentation lr WHERE lr.lawyerEntity.lawyerId = :id " +
                "ORDER BY lr.caseEntity.caseNumber",
                InvolvedCaseProjection.class
            )
            .setParameter("id", lawyerId)
            .getResultList()
            .stream()
            .map(p ->
                new InvolvedCaseRow(
                    p.caseId().toString(),
                    p.caseNumber(),
                    p.title(),
                    "Counsel for " + p.participantName() +
                    " (" + prettyStatus(p.partyType()) + ")",
                    prettyStatus(p.statusName()),
                    statusBadgeClass(p.statusName())
                )
            )
            .toList();
    }

    /**
     * Header/information cards for the Judge Profile page. Throws if the id is
     * not a real judge so the controller can render a 404.
     */
    @Cacheable("judgeDetail")
    @Transactional(readOnly = true)
    public JudgeProfile findJudgeProfile(UUID judgeId) {
        JudgeProfileProjection p;
        try {
            p = entityManager
                .createQuery(
                    "SELECT new kh.edu.paragoniu.court_portal.legal.JudgeProfileProjection(" +
                    "j.judgeId, j.firstName, j.lastName, j.licenseNumber, " +
                    "j.profilePicturePath) " +
                    "FROM Judge j WHERE j.judgeId = :id",
                    JudgeProfileProjection.class
                )
                .setParameter("id", judgeId)
                .getSingleResult();
        } catch (NoResultException ex) {
            throw new CaseDetailNotFoundException(judgeId);
        }

        String name = fullName(p.firstName(), p.lastName());
        String path = p.profilePicturePath();
        String imageUrl = path != null && path.startsWith("http") ? path : null;
        return new JudgeProfile(
            p.judgeId().toString(),
            name,
            initials(name),
            p.firstName(),
            p.lastName(),
            p.licenseNumber(),
            imageUrl
        );
    }

    /** The cases a judge is assigned to (via case_judges). */
    @Cacheable("judgeCases")
    @Transactional(readOnly = true)
    public List<InvolvedCaseRow> findJudgeCases(UUID judgeId) {
        return entityManager
            .createQuery(
                "SELECT new kh.edu.paragoniu.court_portal.legal.JudgeInvolvedCaseProjection(" +
                "cj.caseEntity.caseId, cj.caseEntity.caseNumber, cj.caseEntity.title, " +
                "cj.isPresiding, cj.caseEntity.status.name) " +
                "FROM CaseJudge cj WHERE cj.judgeEntity.judgeId = :id " +
                "ORDER BY cj.caseEntity.caseNumber",
                JudgeInvolvedCaseProjection.class
            )
            .setParameter("id", judgeId)
            .getResultList()
            .stream()
            .map(p ->
                new InvolvedCaseRow(
                    p.caseId().toString(),
                    p.caseNumber(),
                    p.title(),
                    p.presiding() ? "Presiding Judge" : "Associate Judge",
                    prettyStatus(p.statusName()),
                    statusBadgeClass(p.statusName())
                )
            )
            .toList();
    }

    /**
     * Registers a new lawyer. Bar number (license) must be unique. Evicts the
     * lawyer list so the directory shows the new entry.
     */
    @CacheEvict(value = "lawyerList", allEntries = true)
    @Transactional
    public UUID createLawyer(CreateLawyerForm form) {
        String firstName = trim(form.getFirstName());
        String lastName = trim(form.getLastName());
        String barNumber = trim(form.getBarNumber());

        if (firstName == null) {
            throw new LawyerJudgeException("firstName", "First name is required.");
        }
        if (lastName == null) {
            throw new LawyerJudgeException("lastName", "Last name is required.");
        }
        if (barNumber == null) {
            throw new LawyerJudgeException("barNumber", "Bar number is required.");
        }
        if (lawyerRepository.findByLicenseNumber(barNumber).isPresent()) {
            throw new LawyerJudgeException(
                "barNumber",
                "A lawyer with this bar number already exists."
            );
        }

        Lawyer lawyer = new Lawyer();
        lawyer.setFirstName(firstName);
        lawyer.setLastName(lastName);
        lawyer.setLicenseNumber(barNumber);
        lawyer.setFirmName(trim(form.getFirmName()));
        lawyer.setProfilePicturePath(resolveProfilePicture(form.getProfileImage()));
        lawyer.setActive(true);

        return lawyerRepository.save(lawyer).getLawyerId();
    }

    /**
     * Registers a new judge. Bar number (license) must be unique. Evicts the
     * judge list so the directory shows the new entry.
     */
    @CacheEvict(value = "judgeList", allEntries = true)
    @Transactional
    public UUID createJudge(CreateJudgeForm form) {
        String firstName = trim(form.getFirstName());
        String lastName = trim(form.getLastName());
        String barNumber = trim(form.getBarNumber());

        if (firstName == null) {
            throw new LawyerJudgeException("firstName", "First name is required.");
        }
        if (lastName == null) {
            throw new LawyerJudgeException("lastName", "Last name is required.");
        }
        if (barNumber == null) {
            throw new LawyerJudgeException("barNumber", "Bar number is required.");
        }
        if (judgeRepository.findByLicenseNumber(barNumber).isPresent()) {
            throw new LawyerJudgeException(
                "barNumber",
                "A judge with this bar number already exists."
            );
        }

        Judge judge = new Judge();
        judge.setFirstName(firstName);
        judge.setLastName(lastName);
        judge.setLicenseNumber(barNumber);
        judge.setProfilePicturePath(resolveProfilePicture(form.getProfileImage()));
        judge.setActive(true);

        return judgeRepository.save(judge).getJudgeId();
    }

    /**
     * Updates an existing lawyer's details. Bar number must stay unique (a clash
     * with another lawyer is rejected). A new photo replaces the old one; leaving
     * the upload empty keeps the current picture. Evicts the lawyer list and the
     * cached profile so the directory and profile page reflect the change.
     */
    @Caching(evict = {
        @CacheEvict(value = "lawyerList", allEntries = true),
        @CacheEvict(value = "lawyerDetail", allEntries = true)
    })
    @Transactional
    public void updateLawyer(UUID lawyerId, CreateLawyerForm form) {
        Lawyer lawyer = lawyerRepository
            .findById(lawyerId)
            .orElseThrow(() -> new CaseDetailNotFoundException(lawyerId));

        String firstName = requireField(form.getFirstName(), "firstName", "First name");
        String lastName = requireField(form.getLastName(), "lastName", "Last name");
        String barNumber = requireField(form.getBarNumber(), "barNumber", "Bar number");

        lawyerRepository.findByLicenseNumber(barNumber).ifPresent(other -> {
            if (!other.getLawyerId().equals(lawyerId)) {
                throw new LawyerJudgeException(
                    "barNumber",
                    "A lawyer with this bar number already exists."
                );
            }
        });

        lawyer.setFirstName(firstName);
        lawyer.setLastName(lastName);
        lawyer.setLicenseNumber(barNumber);
        lawyer.setFirmName(trim(form.getFirmName()));
        applyNewPicture(form.getProfileImage(), lawyer::setProfilePicturePath);

        lawyerRepository.save(lawyer);
    }

    /**
     * Updates an existing judge's details. Bar number must stay unique. A new
     * photo replaces the old one; an empty upload keeps the current picture.
     * Evicts the judge list and the cached profile.
     */
    @Caching(evict = {
        @CacheEvict(value = "judgeList", allEntries = true),
        @CacheEvict(value = "judgeDetail", allEntries = true)
    })
    @Transactional
    public void updateJudge(UUID judgeId, CreateJudgeForm form) {
        Judge judge = judgeRepository
            .findById(judgeId)
            .orElseThrow(() -> new CaseDetailNotFoundException(judgeId));

        String firstName = requireField(form.getFirstName(), "firstName", "First name");
        String lastName = requireField(form.getLastName(), "lastName", "Last name");
        String barNumber = requireField(form.getBarNumber(), "barNumber", "Bar number");

        judgeRepository.findByLicenseNumber(barNumber).ifPresent(other -> {
            if (!other.getJudgeId().equals(judgeId)) {
                throw new LawyerJudgeException(
                    "barNumber",
                    "A judge with this bar number already exists."
                );
            }
        });

        judge.setFirstName(firstName);
        judge.setLastName(lastName);
        judge.setLicenseNumber(barNumber);
        applyNewPicture(form.getProfileImage(), judge::setProfilePicturePath);

        judgeRepository.save(judge);
    }

    private String requireField(String value, String field, String label) {
        String trimmed = trim(value);
        if (trimmed == null) {
            throw new LawyerJudgeException(field, label + " is required.");
        }
        return trimmed;
    }

    /** Uploads and applies a new picture only when a file was actually chosen. */
    private void applyNewPicture(
        MultipartFile file,
        java.util.function.Consumer<String> setter
    ) {
        if (file == null || file.isEmpty()) {
            return;
        }
        String path = resolveProfilePicture(file);
        if (!NO_PICTURE.equals(path)) {
            setter.accept(path);
        }
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
                "lawyers",
                Objects.requireNonNull(file.getOriginalFilename()),
                file.getBytes(),
                file.getContentType()
            );
        } catch (IOException | RuntimeException ex) {
            throw new LawyerJudgeException(
                "profileImage",
                "The photo could not be uploaded. Please try again."
            );
        }
    }

    private String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String fullName(String first, String last) {
        return ((first == null ? "" : first) + " " + (last == null ? "" : last))
            .trim();
    }

    private String initials(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            return "?";
        }
        String[] parts = fullName.trim().split("\\s+");
        String first = parts[0].substring(0, 1);
        String second = parts.length > 1
            ? parts[parts.length - 1].substring(0, 1)
            : "";
        return (first + second).toUpperCase(Locale.ENGLISH);
    }

    /** Title-cases an enum-style status name (FILING_OPEN -> "Filing Open"). */
    private String prettyStatus(String statusName) {
        if (statusName == null || statusName.isBlank()) {
            return "";
        }
        String[] words = statusName.toLowerCase(Locale.ENGLISH).split("_");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (w.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        return sb.toString();
    }

    private String statusBadgeClass(String status) {
        return switch (status == null ? "" : status) {
            case "FILING_OPEN" -> "badge--green";
            case "SCHEDULED" -> "badge--blue";
            case "IN_TRIAL" -> "badge--indigo";
            case "ADJOURNED" -> "badge--amber";
            case "UNDER_APPEAL" -> "badge--purple";
            case "DISPOSED" -> "badge--gray";
            case "DRAFT" -> "badge--slate";
            default -> "badge--gray";
        };
    }
}
