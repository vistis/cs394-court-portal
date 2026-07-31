package kh.edu.paragoniu.court_portal.settings;

import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Backs the System Settings taxonomy screens (case classifications, statuses,
 * hearing types, disposition outcomes). All four are {@code (id, name)} lookup
 * tables, so one generic service handles them via native SQL — the table and
 * column names come from {@link TaxonomyKind} (never user input), and the only
 * user-supplied value (the name) is always a bound parameter.
 *
 * <p>Deletes are blocked while any fact row still references the value, so the
 * database's foreign keys are never violated from this screen.
 */
@Service
@RequiredArgsConstructor
public class TaxonomyService {

    private final EntityManager entityManager;

    @Transactional(readOnly = true)
    public List<TaxonomyRow> list(TaxonomyKind kind) {
        String sql =
            "SELECT t." + kind.idColumn() + " AS id, t.name AS name, " +
            "(SELECT COUNT(*) FROM " + kind.refTable() + " r " +
            "WHERE r." + kind.refColumn() + " = t." + kind.idColumn() + ") AS cnt " +
            "FROM " + kind.table() + " t ORDER BY t." + kind.idColumn();

        @SuppressWarnings("unchecked")
        List<Object[]> rows = entityManager.createNativeQuery(sql).getResultList();

        return rows
            .stream()
            .map(r -> toRow(kind, r))
            .toList();
    }

    // Adding/removing a taxonomy value changes the classification/status/
    // hearing-type/outcome reference lists cached under "refData" (case filters,
    // create-case dropdowns, hearing-type filters). Evict them so the new value
    // shows up immediately instead of after the 10-minute TTL.
    @CacheEvict(value = "refData", allEntries = true)
    @Transactional
    public void add(TaxonomyKind kind, String rawName) {
        String name = rawName == null ? "" : rawName.trim();
        if (name.isEmpty()) {
            throw new TaxonomyException("Please enter a name.");
        }
        if (name.length() > 100) {
            throw new TaxonomyException("Name must be 100 characters or fewer.");
        }

        Number existing = (Number) entityManager
            .createNativeQuery(
                "SELECT COUNT(*) FROM " + kind.table() +
                " WHERE LOWER(name) = LOWER(:name)"
            )
            .setParameter("name", name)
            .getSingleResult();
        if (existing.longValue() > 0) {
            throw new TaxonomyException("\"" + name + "\" already exists.");
        }

        // Assign the id explicitly as MAX(id)+1 rather than relying on the
        // identity sequence: the seed data was inserted with explicit ids
        // without advancing the sequence, so a sequence-driven insert collides
        // with existing rows. This keeps inserts working without touching the
        // database sequences.
        entityManager
            .createNativeQuery(
                "INSERT INTO " + kind.table() + " (" + kind.idColumn() + ", name) " +
                "VALUES ((SELECT COALESCE(MAX(" + kind.idColumn() + "), 0) + 1 FROM " +
                kind.table() + "), :name)"
            )
            .setParameter("name", name)
            .executeUpdate();
    }

    @CacheEvict(value = "refData", allEntries = true)
    @Transactional
    public void delete(TaxonomyKind kind, int id) {
        Number usage = (Number) entityManager
            .createNativeQuery(
                "SELECT COUNT(*) FROM " + kind.refTable() +
                " WHERE " + kind.refColumn() + " = :id"
            )
            .setParameter("id", id)
            .getSingleResult();
        if (usage.longValue() > 0) {
            throw new TaxonomyException(
                "This value is in use by " + usage.longValue() +
                " record(s) and cannot be deleted."
            );
        }

        int deleted = entityManager
            .createNativeQuery(
                "DELETE FROM " + kind.table() + " WHERE " + kind.idColumn() + " = :id"
            )
            .setParameter("id", id)
            .executeUpdate();
        if (deleted == 0) {
            throw new TaxonomyException("That entry no longer exists.");
        }
    }

    private TaxonomyRow toRow(TaxonomyKind kind, Object[] r) {
        int id = ((Number) r[0]).intValue();
        String name = (String) r[1];
        long count = ((Number) r[2]).longValue();
        String display = kind.prettifyName() ? prettify(name) : name;
        return new TaxonomyRow(
            id,
            kind.zeroPadId() ? String.format("%03d", id) : String.valueOf(id),
            name,
            display,
            kind.showBadge() ? badgeClass(name) : null,
            count,
            count == 0
        );
    }

    /** Title-cases an enum-style name (FILING_OPEN -> "Filing Open"). */
    private String prettify(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String[] words = value.toLowerCase(Locale.ENGLISH).split("[_\\s]+");
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

    /** Badge colour for a case status, mirroring the rest of the app. */
    private String badgeClass(String status) {
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
