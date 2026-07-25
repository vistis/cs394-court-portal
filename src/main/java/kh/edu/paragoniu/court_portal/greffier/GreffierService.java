package kh.edu.paragoniu.court_portal.greffier;

import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.persistence.TypedQuery;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import kh.edu.paragoniu.court_portal.cases.CaseDetailNotFoundException;
import kh.edu.paragoniu.court_shared.entity.Case;
import kh.edu.paragoniu.court_shared.entity.CaseAssignment;
import kh.edu.paragoniu.court_shared.entity.User;
import kh.edu.paragoniu.court_shared.repository.CaseAssignmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Powers the Greffier Management screen (Chief Greffier only): a searchable,
 * paginated list of every user holding the GREFFIER role, with each greffier's
 * live assigned-case count. All data comes from the database via JPQL
 * (mirroring {@code HearingScheduleService}); nothing is hard-coded.
 */
@Service
@RequiredArgsConstructor
public class GreffierService {

    private static final String GREFFIER_ROLE = "GREFFIER";

    private static final ZoneId DISPLAY_ZONE = ZoneId.of("Asia/Phnom_Penh");

    private static final DateTimeFormatter DATE_TIME_FMT = DateTimeFormatter
        .ofPattern("MMM dd, yyyy, hh:mm a", Locale.ENGLISH)
        .withZone(DISPLAY_ZONE);

    private final EntityManager entityManager;
    private final CaseAssignmentRepository caseAssignmentRepository;

    @Cacheable("greffierList")
    @Transactional(readOnly = true)
    public Page<GreffierRow> search(String query, Pageable pageable) {
        List<String> conditions = new ArrayList<>();
        conditions.add("ur.systemRole.name = :role");

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("role", GREFFIER_ROLE);

        if (query != null && !query.isBlank()) {
            conditions.add(
                "(LOWER(u.firstName) LIKE :q OR LOWER(u.lastName) LIKE :q " +
                "OR LOWER(CONCAT(u.firstName, ' ', u.lastName)) LIKE :q)"
            );
            params.put("q", "%" + query.toLowerCase(Locale.ENGLISH).trim() + "%");
        }

        String whereClause = " WHERE " + String.join(" AND ", conditions);

        TypedQuery<Long> countQuery = entityManager.createQuery(
            "SELECT COUNT(DISTINCT u.userId) FROM User u JOIN u.userRoles ur" +
            whereClause,
            Long.class
        );
        params.forEach(countQuery::setParameter);
        long total = countQuery.getSingleResult();

        TypedQuery<GreffierProjection> dataQuery = entityManager.createQuery(
            "SELECT new kh.edu.paragoniu.court_portal.greffier.GreffierProjection(" +
            "u.userId, u.firstName, u.lastName, u.email, ur.systemRole.name, " +
            "(SELECT COUNT(ca) FROM CaseAssignment ca " +
            "WHERE ca.greffierEntity.userId = u.userId)) " +
            "FROM User u JOIN u.userRoles ur" +
            whereClause +
            " ORDER BY u.firstName, u.lastName",
            GreffierProjection.class
        );
        params.forEach(dataQuery::setParameter);
        dataQuery.setFirstResult((int) pageable.getOffset());
        dataQuery.setMaxResults(pageable.getPageSize());

        List<GreffierRow> rows = dataQuery
            .getResultList()
            .stream()
            .map(this::toRow)
            .toList();

        return new PageImpl<>(rows, pageable, total);
    }

    /**
     * Display name of a greffier for the detail-page subtitle. Restricted to
     * users actually holding the GREFFIER role; throws if none matches so the
     * controller can render a 404.
     */
    @Cacheable("greffierNames")
    @Transactional(readOnly = true)
    public String findGreffierName(UUID greffierId) {
        try {
            return entityManager
                .createQuery(
                    "SELECT CONCAT(u.firstName, ' ', u.lastName) " +
                    "FROM User u JOIN u.userRoles ur " +
                    "WHERE u.userId = :id AND ur.systemRole.name = :role",
                    String.class
                )
                .setParameter("id", greffierId)
                .setParameter("role", GREFFIER_ROLE)
                .getSingleResult();
        } catch (NoResultException ex) {
            throw new CaseDetailNotFoundException(greffierId);
        }
    }

    /** All cases assigned to a greffier, searchable by case number/title. */
    @Cacheable("assignedCases")
    @Transactional(readOnly = true)
    public Page<AssignedCaseRow> findAssignedCases(
        UUID greffierId,
        String query,
        Pageable pageable
    ) {
        List<String> conditions = new ArrayList<>();
        conditions.add("ca.greffierEntity.userId = :greffierId");

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("greffierId", greffierId);

        if (query != null && !query.isBlank()) {
            conditions.add(
                "(LOWER(ca.caseEntity.caseNumber) LIKE :q " +
                "OR LOWER(ca.caseEntity.title) LIKE :q)"
            );
            params.put("q", "%" + query.toLowerCase(Locale.ENGLISH).trim() + "%");
        }

        String whereClause = " WHERE " + String.join(" AND ", conditions);

        TypedQuery<Long> countQuery = entityManager.createQuery(
            "SELECT COUNT(ca) FROM CaseAssignment ca" + whereClause,
            Long.class
        );
        params.forEach(countQuery::setParameter);
        long total = countQuery.getSingleResult();

        TypedQuery<GreffierCaseProjection> dataQuery = entityManager.createQuery(
            "SELECT new kh.edu.paragoniu.court_portal.greffier.GreffierCaseProjection(" +
            "ca.caseEntity.caseId, ca.caseEntity.caseNumber, ca.caseEntity.title, " +
            "ca.caseEntity.classification.name, ca.caseEntity.status.name, " +
            "ca.assignedBy.firstName, ca.assignedBy.lastName, ca.assignedAt) " +
            "FROM CaseAssignment ca" +
            whereClause +
            " ORDER BY ca.assignedAt DESC",
            GreffierCaseProjection.class
        );
        params.forEach(dataQuery::setParameter);
        dataQuery.setFirstResult((int) pageable.getOffset());
        dataQuery.setMaxResults(pageable.getPageSize());

        List<AssignedCaseRow> rows = dataQuery
            .getResultList()
            .stream()
            .map(this::toAssignedRow)
            .toList();

        return new PageImpl<>(rows, pageable, total);
    }

    /**
     * Case suggestions for the Assign Case modal. Cases the greffier already
     * holds are flagged (not hidden) so the chief can see them but can't
     * double-assign. Not cached — it changes per keystroke.
     */
    @Transactional(readOnly = true)
    public List<AssignCaseOption> searchAssignableCases(
        UUID greffierId,
        String query,
        int limit
    ) {
        Set<UUID> assigned = new HashSet<>(
            entityManager
                .createQuery(
                    "SELECT ca.caseEntity.caseId FROM CaseAssignment ca " +
                    "WHERE ca.greffierEntity.userId = :gid",
                    UUID.class
                )
                .setParameter("gid", greffierId)
                .getResultList()
        );

        boolean hasQuery = query != null && !query.isBlank();
        String jpql =
            "SELECT new kh.edu.paragoniu.court_portal.greffier.AssignCaseProjection(" +
            "c.caseId, c.caseNumber, c.title, c.status.name) FROM Case c";
        if (hasQuery) {
            jpql += " WHERE LOWER(c.caseNumber) LIKE :q OR LOWER(c.title) LIKE :q";
        }
        jpql += " ORDER BY c.caseNumber";

        TypedQuery<AssignCaseProjection> q = entityManager.createQuery(
            jpql,
            AssignCaseProjection.class
        );
        if (hasQuery) {
            q.setParameter("q", "%" + query.toLowerCase(Locale.ENGLISH).trim() + "%");
        }
        q.setMaxResults(limit);

        return q
            .getResultList()
            .stream()
            .map(p ->
                new AssignCaseOption(
                    p.caseId().toString(),
                    p.caseNumber(),
                    p.title(),
                    prettyRole(p.statusName()),
                    statusBadgeClass(p.statusName()),
                    assigned.contains(p.caseId())
                )
            )
            .toList();
    }

    /**
     * Assign an existing case to a greffier. Evicts the greffier's assigned-case
     * list and the greffier list (its assigned-count column). Returns the case
     * number for the confirmation message.
     */
    @Caching(evict = {
        @CacheEvict(value = "assignedCases", allEntries = true),
        @CacheEvict(value = "greffierList", allEntries = true)
    })
    @Transactional
    public String assignCase(UUID greffierId, UUID caseId, UUID assignedById) {
        // Validate the target is an actual greffier.
        String greffierName = entityManager
            .createQuery(
                "SELECT CONCAT(u.firstName, ' ', u.lastName) " +
                "FROM User u JOIN u.userRoles ur " +
                "WHERE u.userId = :id AND ur.systemRole.name = :role",
                String.class
            )
            .setParameter("id", greffierId)
            .setParameter("role", GREFFIER_ROLE)
            .getResultStream()
            .findFirst()
            .orElseThrow(() -> new CaseDetailNotFoundException(greffierId));

        String caseNumber = entityManager
            .createQuery(
                "SELECT c.caseNumber FROM Case c WHERE c.caseId = :id",
                String.class
            )
            .setParameter("id", caseId)
            .getResultStream()
            .findFirst()
            .orElseThrow(() -> new CaseDetailNotFoundException(caseId));

        if (caseAssignmentRepository.isGreffierAssignedToCase(caseId, greffierId)) {
            throw new IllegalStateException(
                "Case " + caseNumber + " is already assigned to " + greffierName + "."
            );
        }

        CaseAssignment assignment = new CaseAssignment();
        assignment.setCaseEntity(entityManager.getReference(Case.class, caseId));
        assignment.setGreffierEntity(
            entityManager.getReference(User.class, greffierId)
        );
        assignment.setAssignedBy(
            entityManager.getReference(User.class, assignedById)
        );
        assignment.setAssignedAt(java.time.Instant.now());
        caseAssignmentRepository.save(assignment);

        return caseNumber;
    }

    private AssignedCaseRow toAssignedRow(GreffierCaseProjection p) {
        String assignedBy = ((p.assignedByFirstName() == null
                    ? ""
                    : p.assignedByFirstName()) +
            " " +
            (p.assignedByLastName() == null ? "" : p.assignedByLastName())).trim();
        return new AssignedCaseRow(
            p.caseId().toString(),
            p.caseNumber(),
            p.title(),
            prettyRole(p.classificationName()),
            prettyRole(p.statusName()),
            statusBadgeClass(p.statusName()),
            assignedBy.isBlank() ? "—" : assignedBy,
            formatDateTime(p.assignedAt())
        );
    }

    private String formatDateTime(Instant instant) {
        return instant == null ? "" : DATE_TIME_FMT.format(instant);
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

    private GreffierRow toRow(GreffierProjection p) {
        String fullName = ((p.firstName() == null ? "" : p.firstName()) +
            " " +
            (p.lastName() == null ? "" : p.lastName())).trim();
        return new GreffierRow(
            p.userId().toString(),
            fullName,
            initials(fullName),
            p.email(),
            prettyRole(p.roleName()),
            p.assignedCases() == null ? 0L : p.assignedCases()
        );
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

    private String prettyRole(String roleName) {
        if (roleName == null || roleName.isBlank()) {
            return "";
        }
        String[] words = roleName.toLowerCase(Locale.ENGLISH).split("_");
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
}
