package kh.edu.paragoniu.court_portal.hearings;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Lightweight case search that powers the Case Reference typeahead. */
@Service
@RequiredArgsConstructor
public class CaseLookupService {

    private final EntityManager entityManager;

    @Transactional(readOnly = true)
    public List<CaseLookupResult> lookup(String q, int limit) {
        boolean hasQuery = q != null && !q.isBlank();
        String jpql =
            "SELECT new kh.edu.paragoniu.court_portal.hearings.CaseLookupResult(" +
            "c.caseId, c.caseNumber, c.title) FROM Case c";
        if (hasQuery) {
            jpql +=
                " WHERE LOWER(c.caseNumber) LIKE :q OR LOWER(c.title) LIKE :q";
        }
        jpql += " ORDER BY c.caseNumber";

        TypedQuery<CaseLookupResult> query = entityManager.createQuery(
            jpql,
            CaseLookupResult.class
        );
        if (hasQuery) {
            query.setParameter("q", "%" + q.toLowerCase().trim() + "%");
        }
        query.setMaxResults(limit);
        return query.getResultList();
    }
}
