package kh.edu.paragoniu.court_portal.cases;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import kh.edu.paragoniu.court_shared.entity.Case;
import kh.edu.paragoniu.court_shared.entity.CaseJudge;
import kh.edu.paragoniu.court_shared.entity.Courtroom;
import kh.edu.paragoniu.court_shared.entity.Docket;
import kh.edu.paragoniu.court_shared.entity.Hearing;
import kh.edu.paragoniu.court_shared.entity.HearingType;
import kh.edu.paragoniu.court_shared.repository.CaseJudgeRepository;
import kh.edu.paragoniu.court_shared.repository.CaseRepository;
import kh.edu.paragoniu.court_shared.repository.CourtroomRepository;
import kh.edu.paragoniu.court_shared.repository.HearingRepository;
import kh.edu.paragoniu.court_shared.repository.HearingTypeRepository;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HearingService {

    private static final ZoneId DISPLAY_ZONE = ZoneId.of("Asia/Phnom_Penh");
    private static final String INITIAL_STATUS = "SCHEDULED";

    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter
        .ofPattern("MMM", Locale.ENGLISH)
        .withZone(DISPLAY_ZONE);
    private static final DateTimeFormatter DAY_FMT = DateTimeFormatter
        .ofPattern("dd", Locale.ENGLISH)
        .withZone(DISPLAY_ZONE);
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter
        .ofPattern("hh:mm a", Locale.ENGLISH)
        .withZone(DISPLAY_ZONE);
    private static final DateTimeFormatter DOCKET_DATE_TIME_FMT =
        DateTimeFormatter
            .ofPattern("MMM dd, yyyy, hh:mm a", Locale.ENGLISH)
            .withZone(DISPLAY_ZONE);
    private static final DateTimeFormatter CURRENT_SCHEDULE_FMT =
        DateTimeFormatter
            .ofPattern("dd MMM yyyy, hh:mm a", Locale.ENGLISH)
            .withZone(DISPLAY_ZONE);
    private static final DateTimeFormatter FORM_DATE_TIME_FMT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm", Locale.ENGLISH);

    private final CaseRepository caseRepository;
    private final HearingRepository hearingRepository;
    private final HearingTypeRepository hearingTypeRepository;
    private final CourtroomRepository courtroomRepository;
    private final CaseJudgeRepository caseJudgeRepository;
    private final MongoTemplate mongoTemplate;

    public HearingService(
        CaseRepository caseRepository,
        HearingRepository hearingRepository,
        HearingTypeRepository hearingTypeRepository,
        CourtroomRepository courtroomRepository,
        CaseJudgeRepository caseJudgeRepository,
        MongoTemplate mongoTemplate
    ) {
        this.caseRepository = caseRepository;
        this.hearingRepository = hearingRepository;
        this.hearingTypeRepository = hearingTypeRepository;
        this.courtroomRepository = courtroomRepository;
        this.caseJudgeRepository = caseJudgeRepository;
        this.mongoTemplate = mongoTemplate;
    }

    @Transactional(readOnly = true)
    public List<HearingRow> findUpcomingHearings(UUID caseId) {
        ensureCaseExists(caseId);
        Instant now = Instant.now();
        return hearingRepository
            .findByCaseEntityCaseId(caseId)
            .stream()
            .filter(hearing -> hearing.getStartAt().isAfter(now))
            .sorted(Comparator.comparing(Hearing::getStartAt))
            .map(this::toRow)
            .toList();
    }

    @Transactional(readOnly = true)
    public List<HearingRow> findPastHearings(UUID caseId) {
        ensureCaseExists(caseId);
        Instant now = Instant.now();
        return hearingRepository
            .findByCaseEntityCaseId(caseId)
            .stream()
            .filter(hearing -> !hearing.getStartAt().isAfter(now))
            .sorted(Comparator.comparing(Hearing::getStartAt).reversed())
            .map(this::toRow)
            .toList();
    }

    @Transactional(readOnly = true)
    public List<FilterOption> findHearingTypeOptions() {
        return hearingTypeRepository
            .findAll(Sort.by("name"))
            .stream()
            .map(type -> new FilterOption(type.getHearingTypeId(), type.getName()))
            .toList();
    }

    @Transactional(readOnly = true)
    public List<FilterOption> findCourtroomOptions() {
        return courtroomRepository
            .findAll(Sort.by("roomNumber"))
            .stream()
            .map(room -> new FilterOption(room.getCourtroomId(), room.getRoomNumber()))
            .toList();
    }

    @Transactional(readOnly = true)
    public HearingRescheduleView findRescheduleView(UUID caseId, UUID hearingId) {
        Hearing hearing = findHearingForCase(caseId, hearingId);
        return new HearingRescheduleView(
            hearing.getHearingId(),
            hearing.getHearingType().getName(),
            CURRENT_SCHEDULE_FMT.format(hearing.getStartAt()) +
            " in " +
            hearing.getCourtroom().getRoomNumber(),
            hearing.getCourtroom().getCourtroomId()
        );
    }

    @Transactional
    @org.springframework.cache.annotation.Caching(
        evict = {
            @org.springframework.cache.annotation.CacheEvict(value = "publicHearings", allEntries = true),
            @org.springframework.cache.annotation.CacheEvict(value = "publicCaseDetail", key = "#caseId")
        }
    )
    public UUID scheduleHearing(
        UUID caseId,
        ScheduleHearingForm form,
        UUID performedById
    ) {
        Case caseEntity = caseRepository
            .findById(caseId)
            .orElseThrow(() -> new CaseDetailNotFoundException(caseId));
        HearingType hearingType = hearingTypeRepository
            .findById(form.getHearingTypeId())
            .orElseThrow(() ->
                new HearingScheduleException(
                    "hearingTypeId",
                    "Selected hearing type does not exist."
                )
            );
        Courtroom courtroom = courtroomRepository
            .findById(form.getCourtroomId())
            .orElseThrow(() ->
                new HearingScheduleException(
                    "courtroomId",
                    "Selected courtroom does not exist."
                )
            );

        Instant startAt = toInstant(form.getStartAt(), "startAt");
        Instant endAt = toInstant(form.getEndAt(), "endAt");
        if (!endAt.isAfter(startAt)) {
            throw new HearingScheduleException(
                "endAt",
                "End date and time must be after the start date and time."
            );
        }
        if (
            hearingRepository.hasRoomOverlap(
                courtroom.getCourtroomId(),
                startAt,
                endAt,
                null
            )
        ) {
            throw new HearingScheduleException(
                "courtroomId",
                "This courtroom already has a scheduled hearing during that time."
            );
        }

        Hearing hearing = new Hearing();
        hearing.setCaseEntity(caseEntity);
        hearing.setHearingType(hearingType);
        hearing.setCourtroom(courtroom);
        hearing.setStartAt(startAt);
        hearing.setEndAt(endAt);
        hearing.setStatus(INITIAL_STATUS);
        Hearing saved = hearingRepository.save(hearing);

        createAutomaticDocketEntry(
            caseId,
            hearingType.getName(),
            courtroom.getRoomNumber(),
            startAt,
            performedById
        );
        return saved.getHearingId();
    }

    @Transactional
    @org.springframework.cache.annotation.Caching(
        evict = {
            @org.springframework.cache.annotation.CacheEvict(value = "publicHearings", allEntries = true),
            @org.springframework.cache.annotation.CacheEvict(value = "publicCaseDetail", key = "#caseId")
        }
    )
    public UUID rescheduleHearing(
        UUID caseId,
        UUID hearingId,
        RescheduleHearingForm form,
        UUID performedById
    ) {
        Hearing hearing = findHearingForCase(caseId, hearingId);
        Courtroom courtroom = courtroomRepository
            .findById(form.getCourtroomId())
            .orElseThrow(() ->
                new HearingScheduleException(
                    "courtroomId",
                    "Selected courtroom does not exist."
                )
            );

        Instant oldStartAt = hearing.getStartAt();
        Instant oldEndAt = hearing.getEndAt();
        String oldCourtroom = hearing.getCourtroom().getRoomNumber();

        Instant startAt = toInstant(form.getStartAt(), "startAt");
        Instant endAt = toInstant(form.getEndAt(), "endAt");
        if (!endAt.isAfter(startAt)) {
            throw new HearingScheduleException(
                "endAt",
                "End date and time must be after the start date and time."
            );
        }
        if (
            hearingRepository.hasRoomOverlap(
                courtroom.getCourtroomId(),
                startAt,
                endAt,
                hearingId
            )
        ) {
            throw new HearingScheduleException(
                "courtroomId",
                "This courtroom already has a scheduled hearing during that time."
            );
        }

        hearing.setCourtroom(courtroom);
        hearing.setStartAt(startAt);
        hearing.setEndAt(endAt);
        Hearing saved = hearingRepository.save(hearing);

        createRescheduleDocketEntry(
            caseId,
            hearing.getHearingType().getName(),
            oldStartAt,
            oldEndAt,
            oldCourtroom,
            startAt,
            endAt,
            courtroom.getRoomNumber(),
            performedById
        );
        return saved.getHearingId();
    }

    private HearingRow toRow(Hearing hearing) {
        return new HearingRow(
            hearing.getHearingId().toString(),
            MONTH_FMT.format(hearing.getStartAt()).toUpperCase(Locale.ENGLISH),
            DAY_FMT.format(hearing.getStartAt()),
            hearing.getHearingType().getName(),
            TIME_FMT.format(hearing.getStartAt()) +
            " - " +
            TIME_FMT.format(hearing.getEndAt()),
            hearing.getCourtroom().getRoomNumber(),
            findPresidingJudgeName(hearing),
            prettify(hearing.getStatus()),
            badgeClass(hearing.getStatus())
        );
    }

    private String findPresidingJudgeName(Hearing hearing) {
        return caseJudgeRepository
            .findByIdCaseId(hearing.getCaseEntity().getCaseId())
            .stream()
            .filter(CaseJudge::isPresiding)
            .findFirst()
            .map(CaseJudge::getJudgeEntity)
            .map(judge ->
                "Hon. " +
                (
                    (judge.getFirstName() == null ? "" : judge.getFirstName()) +
                    " " +
                    (judge.getLastName() == null ? "" : judge.getLastName())
                ).trim()
            )
            .filter(name -> !name.equals("Hon."))
            .orElse("Unassigned");
    }

    private Instant toInstant(String value, String fieldName) {
        try {
            LocalDateTime localDateTime = LocalDateTime.parse(
                value,
                FORM_DATE_TIME_FMT
            );
            return toInstant(localDateTime);
        } catch (DateTimeParseException ex) {
            throw new HearingScheduleException(
                fieldName,
                "Enter a valid date and time."
            );
        }
    }

    private Instant toInstant(LocalDateTime localDateTime) {
        return localDateTime.atZone(DISPLAY_ZONE).toInstant();
    }

    private void ensureCaseExists(UUID caseId) {
        if (!caseRepository.existsById(caseId)) {
            throw new CaseDetailNotFoundException(caseId);
        }
    }

    private Hearing findHearingForCase(UUID caseId, UUID hearingId) {
        Hearing hearing = hearingRepository
            .findById(hearingId)
            .orElseThrow(() -> new CaseDetailNotFoundException(caseId));
        if (!caseId.equals(hearing.getCaseEntity().getCaseId())) {
            throw new CaseDetailNotFoundException(caseId);
        }
        return hearing;
    }

    private void createAutomaticDocketEntry(
        UUID caseId,
        String hearingType,
        String courtroom,
        Instant startAt,
        UUID performedById
    ) {
        Docket docket = new Docket();
        docket.setCaseId(caseId);
        docket.setActivityType("HEARING");
        docket.setDescription(
            "Hearing scheduled: " +
            hearingType +
            " on " +
            DOCKET_DATE_TIME_FMT.format(startAt) +
            " in " +
            courtroom +
            "."
        );
        docket.setPerformedById(performedById);
        docket.setTimestamp(Instant.now());
        mongoTemplate.save(docket);
    }

    private void createRescheduleDocketEntry(
        UUID caseId,
        String hearingType,
        Instant oldStartAt,
        Instant oldEndAt,
        String oldCourtroom,
        Instant newStartAt,
        Instant newEndAt,
        String newCourtroom,
        UUID performedById
    ) {
        Docket docket = new Docket();
        docket.setCaseId(caseId);
        docket.setActivityType("HEARING");
        docket.setDescription(
            "Hearing rescheduled: " +
            hearingType +
            " moved from " +
            DOCKET_DATE_TIME_FMT.format(oldStartAt) +
            " - " +
            TIME_FMT.format(oldEndAt) +
            " in " +
            oldCourtroom +
            " to " +
            DOCKET_DATE_TIME_FMT.format(newStartAt) +
            " - " +
            TIME_FMT.format(newEndAt) +
            " in " +
            newCourtroom +
            "."
        );
        docket.setPerformedById(performedById);
        docket.setTimestamp(Instant.now());
        mongoTemplate.save(docket);
    }

    private String badgeClass(String status) {
        return switch (status) {
            case "SCHEDULED" -> "badge--amber";
            case "COMPLETED" -> "badge--gray";
            case "CANCELLED" -> "badge--red";
            default -> "badge--slate";
        };
    }

    private String prettify(String code) {
        if (code == null || code.isBlank()) {
            return "Unknown";
        }

        String[] parts = code.toLowerCase(Locale.ENGLISH).split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(part.charAt(0)));
            sb.append(part.substring(1));
        }
        return sb.toString();
    }
}
