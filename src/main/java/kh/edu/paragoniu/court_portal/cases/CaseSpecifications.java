package kh.edu.paragoniu.court_portal.cases;

import jakarta.persistence.criteria.Predicate;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import kh.edu.paragoniu.court_shared.entity.Case;
import org.springframework.data.jpa.domain.Specification;

/** Builds a dynamic query for the Cases Directory from the active filters. */
public final class CaseSpecifications {

    private CaseSpecifications() {}

    public static Specification<Case> build(
        String q,
        Integer classificationId,
        Integer statusId,
        LocalDate filedFrom,
        LocalDate filedTo
    ) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (q != null && !q.isBlank()) {
                String like = "%" + q.trim().toLowerCase() + "%";
                predicates.add(
                    cb.or(
                        cb.like(cb.lower(root.get("caseNumber")), like),
                        cb.like(cb.lower(root.get("title")), like)
                    )
                );
            }
            if (classificationId != null) {
                predicates.add(
                    cb.equal(
                        root.get("classification").get("classificationId"),
                        classificationId
                    )
                );
            }
            if (statusId != null) {
                predicates.add(
                    cb.equal(root.get("status").get("statusId"), statusId)
                );
            }
            if (filedFrom != null) {
                predicates.add(
                    cb.greaterThanOrEqualTo(
                        root.get("filedAt"),
                        filedFrom.atStartOfDay(ZoneOffset.UTC).toInstant()
                    )
                );
            }
            if (filedTo != null) {
                predicates.add(
                    cb.lessThan(
                        root.get("filedAt"),
                        filedTo.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant()
                    )
                );
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
