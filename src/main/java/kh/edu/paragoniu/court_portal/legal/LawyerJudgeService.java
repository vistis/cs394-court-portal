package kh.edu.paragoniu.court_portal.legal;

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
import kh.edu.paragoniu.court_shared.entity.Judge;
import kh.edu.paragoniu.court_shared.entity.Lawyer;
import kh.edu.paragoniu.court_shared.repository.JudgeRepository;
import kh.edu.paragoniu.court_shared.repository.LawyerRepository;
import kh.edu.paragoniu.court_shared.service.S3Service;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
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
}
