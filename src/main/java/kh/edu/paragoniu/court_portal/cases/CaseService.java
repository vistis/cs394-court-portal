package kh.edu.paragoniu.court_portal.cases;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.Locale;
import kh.edu.paragoniu.court_shared.entity.Case;
import kh.edu.paragoniu.court_shared.repository.CaseJudgeRepository;
import kh.edu.paragoniu.court_shared.repository.CaseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CaseService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter
        .ofPattern("MMM dd, yyyy", Locale.ENGLISH)
        .withZone(ZoneOffset.UTC);

    private final CaseRepository caseRepository;
    private final CaseJudgeRepository caseJudgeRepository;

    @Transactional(readOnly = true)
    public Page<CaseRow> search(
        String q,
        Integer classificationId,
        Integer statusId,
        LocalDate filedFrom,
        LocalDate filedTo,
        Pageable pageable
    ) {
        Specification<Case> spec = CaseSpecifications.build(
            q,
            classificationId,
            statusId,
            filedFrom,
            filedTo
        );
        return caseRepository.findAll(spec, pageable).map(this::toRow);
    }

    private CaseRow toRow(Case c) {
        String judge = caseJudgeRepository
            .findByIdCaseId(c.getCaseId())
            .stream()
            // Presiding judge first, if any.
            .sorted(Comparator.comparing(cj -> !cj.isPresiding()))
            .findFirst()
            .map(cj ->
                "Hon. " +
                cj.getJudgeEntity().getFirstName() +
                " " +
                cj.getJudgeEntity().getLastName()
            )
            .orElse("—");

        return new CaseRow(
            c.getCaseId(),
            c.getCaseNumber(),
            c.getTitle(),
            c.getClassification().getName(),
            prettify(c.getStatus().getName()),
            badgeClass(c.getStatus().getName()),
            DATE_FMT.format(c.getFiledAt()),
            judge
        );
    }

    /** Maps a raw status code to a badge colour class. */
    private String badgeClass(String status) {
        return switch (status) {
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

    /** FILING_OPEN -> "Filing Open". */
    private String prettify(String code) {
        String[] parts = code.toLowerCase(Locale.ENGLISH).split("_");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));
        }
        return sb.toString();
    }
}
