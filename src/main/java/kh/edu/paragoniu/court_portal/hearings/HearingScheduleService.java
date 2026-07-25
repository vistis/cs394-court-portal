package kh.edu.paragoniu.court_portal.hearings;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import kh.edu.paragoniu.court_portal.cases.CaseDetailNotFoundException;
import kh.edu.paragoniu.court_shared.entity.Case;
import kh.edu.paragoniu.court_shared.entity.Hearing;
import kh.edu.paragoniu.court_shared.entity.HearingType;
import kh.edu.paragoniu.court_shared.repository.HearingRepository;
import kh.edu.paragoniu.court_shared.repository.HearingTypeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Powers the global Hearings Schedule: a filtered, paginated view over all
 * hearings across every case. Uses JPQL via {@link EntityManager} to mirror the
 * existing case-search pattern (no Specification support on HearingRepository).
 */
@Service
@RequiredArgsConstructor
public class HearingScheduleService {

    private static final ZoneId DISPLAY_ZONE = ZoneId.of("Asia/Phnom_Penh");

    private static final DateTimeFormatter DT_FMT = DateTimeFormatter
        .ofPattern("dd MMM yyyy, hh:mm a", Locale.ENGLISH)
        .withZone(DISPLAY_ZONE);

    private final EntityManager entityManager;
    private final HearingTypeRepository hearingTypeRepository;
    private final HearingRepository hearingRepository;

    @Cacheable("hearingList")
    @Transactional(readOnly = true)
    public Page<HearingScheduleRow> search(
        String query,
        Integer hearingTypeId,
        String status,
        LocalDate fromDate,
        LocalDate toDate,
        Pageable pageable
    ) {
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            throw new IllegalArgumentException(
                "From date cannot be after To date."
            );
        }

        List<String> conditions = new ArrayList<>();
        Map<String, Object> params = new LinkedHashMap<>();

        if (query != null && !query.isBlank()) {
            conditions.add("LOWER(h.caseEntity.caseNumber) LIKE :query");
            params.put("query", "%" + query.toLowerCase().trim() + "%");
        }
        if (hearingTypeId != null) {
            conditions.add("h.hearingType.hearingTypeId = :typeId");
            params.put("typeId", hearingTypeId);
        }
        if (status != null && !status.isBlank()) {
            conditions.add("h.status = :status");
            params.put("status", status);
        }
        if (fromDate != null) {
            conditions.add("h.startAt >= :fromAt");
            params.put(
                "fromAt",
                fromDate.atStartOfDay(ZoneOffset.UTC).toInstant()
            );
        }
        if (toDate != null) {
            conditions.add("h.startAt < :toAt");
            params.put(
                "toAt",
                toDate.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant()
            );
        }

        String whereClause = conditions.isEmpty()
            ? ""
            : " WHERE " + String.join(" AND ", conditions);

        TypedQuery<Long> countQuery = entityManager.createQuery(
            "SELECT COUNT(h) FROM Hearing h" + whereClause,
            Long.class
        );
        params.forEach(countQuery::setParameter);
        long total = countQuery.getSingleResult();

        TypedQuery<HearingProjection> dataQuery = entityManager.createQuery(
            "SELECT new kh.edu.paragoniu.court_portal.hearings.HearingProjection(" +
            "h.hearingId, h.caseEntity.caseId, h.caseEntity.caseNumber, h.hearingType.name, " +
            "h.courtroom.roomNumber, h.startAt, h.endAt, h.status) " +
            "FROM Hearing h" +
            whereClause +
            " ORDER BY h.startAt DESC",
            HearingProjection.class
        );
        params.forEach(dataQuery::setParameter);
        dataQuery.setFirstResult((int) pageable.getOffset());
        dataQuery.setMaxResults(pageable.getPageSize());

        List<HearingScheduleRow> rows = dataQuery
            .getResultList()
            .stream()
            .map(this::toRow)
            .toList();

        return new PageImpl<>(rows, pageable, total);
    }

    @Cacheable("hearingDetail")
    @Transactional(readOnly = true)
    public HearingDetailView findDetail(UUID hearingId) {
        Hearing hearing = hearingRepository
            .findById(hearingId)
            .orElseThrow(() -> new CaseDetailNotFoundException(hearingId));
        Case caseEntity = hearing.getCaseEntity();
        return new HearingDetailView(
            hearing.getHearingId().toString(),
            caseEntity.getCaseId().toString(),
            caseEntity.getCaseNumber(),
            caseEntity.getTitle(),
            hearing.getHearingType().getName(),
            hearing.getCourtroom().getRoomNumber(),
            hearing.getCourtroom().getCourtroomId(),
            prettyStatus(hearing.getStatus()),
            badgeClass(hearing.getStatus()),
            DT_FMT.format(hearing.getStartAt()),
            DT_FMT.format(hearing.getEndAt())
        );
    }

    public List<HearingType> hearingTypeOptions() {
        return hearingTypeRepository.findAll();
    }

    @Cacheable(value = "refData", key = "'hearingStatuses'")
    public List<String> statusOptions() {
        return entityManager
            .createQuery(
                "SELECT DISTINCT h.status FROM Hearing h ORDER BY h.status",
                String.class
            )
            .getResultList();
    }

    private HearingScheduleRow toRow(HearingProjection p) {
        return new HearingScheduleRow(
            p.hearingId().toString(),
            p.caseId().toString(),
            p.caseNumber(),
            p.hearingType(),
            p.courtroom(),
            DT_FMT.format(p.startAt()),
            DT_FMT.format(p.endAt()),
            prettyStatus(p.status()),
            badgeClass(p.status())
        );
    }

    private String prettyStatus(String status) {
        if (status == null || status.isBlank()) {
            return "";
        }
        String lower = status.toLowerCase(Locale.ENGLISH).replace('_', ' ');
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    private String badgeClass(String status) {
        return switch (status == null ? "" : status.toUpperCase(Locale.ENGLISH)) {
            case "SCHEDULED" -> "badge--blue";
            case "COMPLETED" -> "badge--gray";
            case "ADJOURNED" -> "badge--amber";
            case "CANCELLED" -> "badge--red";
            default -> "badge--slate";
        };
    }
}
