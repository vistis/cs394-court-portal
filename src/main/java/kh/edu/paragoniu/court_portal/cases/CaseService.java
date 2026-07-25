package kh.edu.paragoniu.court_portal.cases;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import java.lang.reflect.Field;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import kh.edu.paragoniu.court_shared.entity.Case;
import kh.edu.paragoniu.court_shared.entity.CaseClassification;
import kh.edu.paragoniu.court_shared.entity.CaseJudge;
import kh.edu.paragoniu.court_shared.entity.CaseJudgeId;
import kh.edu.paragoniu.court_shared.entity.CaseStatus;
import kh.edu.paragoniu.court_shared.entity.Disposition;
import kh.edu.paragoniu.court_shared.entity.DispositionOutcome;
import kh.edu.paragoniu.court_shared.entity.Docket;
import kh.edu.paragoniu.court_shared.entity.Judge;
import kh.edu.paragoniu.court_shared.entity.Appeal;
import kh.edu.paragoniu.court_shared.repository.CaseClassificationRepository;
import kh.edu.paragoniu.court_shared.repository.CaseJudgeRepository;
import kh.edu.paragoniu.court_shared.repository.CaseRepository;
import kh.edu.paragoniu.court_shared.repository.AppealRepository;
import kh.edu.paragoniu.court_shared.repository.DispositionOutcomeRepository;
import kh.edu.paragoniu.court_shared.repository.DispositionRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CaseService {

    private static final ZoneId DISPLAY_ZONE = ZoneId.of("Asia/Phnom_Penh");

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter
        .ofPattern("MMM dd, yyyy", Locale.ENGLISH)
        .withZone(DISPLAY_ZONE);

    private static final DateTimeFormatter DATE_TIME_FMT = DateTimeFormatter
        .ofPattern("MMM dd, yyyy, hh:mm a", Locale.ENGLISH)
        .withZone(DISPLAY_ZONE);

    private static final List<String> MANUAL_DOCKET_ACTIVITY_TYPES = List.of(
        "FILING",
        "EVIDENCE",
        "HEARING"
    );

    private final EntityManager entityManager;
    private final CaseRepository caseRepository;
    private final CaseClassificationRepository classificationRepository;
    private final CaseJudgeRepository caseJudgeRepository;
    private final DispositionRepository dispositionRepository;
    private final DispositionOutcomeRepository dispositionOutcomeRepository;
    private final AppealRepository appealRepository;
    private final MongoTemplate mongoTemplate;

    public CaseService(
        EntityManager entityManager,
        CaseRepository caseRepository,
        CaseClassificationRepository classificationRepository,
        CaseJudgeRepository caseJudgeRepository,
        DispositionRepository dispositionRepository,
        DispositionOutcomeRepository dispositionOutcomeRepository,
        AppealRepository appealRepository,
        MongoTemplate mongoTemplate
    ) {
        this.entityManager = entityManager;
        this.caseRepository = caseRepository;
        this.classificationRepository = classificationRepository;
        this.caseJudgeRepository = caseJudgeRepository;
        this.dispositionRepository = dispositionRepository;
        this.dispositionOutcomeRepository = dispositionOutcomeRepository;
        this.appealRepository = appealRepository;
        this.mongoTemplate = mongoTemplate;
    }

    @Cacheable("caseList")
    @Transactional(readOnly = true)
    public Page<CaseRow> search(
        String query,
        Integer classificationId,
        Integer statusId,
        LocalDate filedFrom,
        LocalDate filedTo,
        Pageable pageable
    ) {
        if (
            filedFrom != null && filedTo != null && filedFrom.isAfter(filedTo)
        ) {
            throw new IllegalArgumentException(
                "Filed-from date cannot be after filed-to date."
            );
        }

        QueryParams params = QueryParams.from(
            query,
            classificationId,
            statusId,
            filedFrom,
            filedTo
        );
        String whereClause = params.whereClause();

        TypedQuery<CaseRowProjection> dataQuery = entityManager.createQuery(
            """
            SELECT new kh.edu.paragoniu.court_portal.cases.CaseRowProjection(
                c.caseId,
                c.caseNumber,
                c.title,
                c.classification.name,
                c.status.name,
                c.filedAt,
                j.firstName,
                j.lastName
            )
            FROM Case c
            LEFT JOIN CaseJudge cj
                ON cj.caseEntity = c
                AND cj.isPresiding = true
            LEFT JOIN cj.judgeEntity j
            """ +
            whereClause +
            " ORDER BY c.filedAt DESC, c.caseId DESC",
            CaseRowProjection.class
        );

        TypedQuery<Long> countQuery = entityManager.createQuery(
            """
            SELECT COUNT(DISTINCT c)
            FROM Case c
            """ +
            whereClause,
            Long.class
        );

        params.applyTo(dataQuery);
        params.applyTo(countQuery);

        List<CaseRow> rows = dataQuery
            .setFirstResult((int) pageable.getOffset())
            .setMaxResults(pageable.getPageSize())
            .getResultList()
            .stream()
            .map(this::toRow)
            .toList();
        long total = countQuery.getSingleResult();

        return new PageImpl<>(rows, pageable, total);
    }

    @Cacheable(value = "refData", key = "'caseClassifications'")
    @Transactional(readOnly = true)
    public List<FilterOption> findClassificationOptions() {
        return entityManager
            .createQuery(
                """
                SELECT new kh.edu.paragoniu.court_portal.cases.FilterOption(
                    c.classificationId,
                    c.name
                )
                FROM CaseClassification c
                ORDER BY c.name
                """,
                FilterOption.class
            )
            .getResultList();
    }

    @Cacheable(value = "refData", key = "'caseStatuses'")
    @Transactional(readOnly = true)
    public List<FilterOption> findStatusOptions() {
        return entityManager
            .createQuery(
                """
                SELECT new kh.edu.paragoniu.court_portal.cases.FilterOption(
                    s.statusId,
                    s.name
                )
                FROM CaseStatus s
                ORDER BY s.name
                """,
                FilterOption.class
            )
            .getResultList();
    }

    @Cacheable(value = "refData", key = "'dispositionOutcomes'")
    @Transactional(readOnly = true)
    public List<FilterOption> findDispositionOutcomeOptions() {
        return dispositionOutcomeRepository
            .findAll(Sort.by(Sort.Direction.ASC, "outcomeTypeId"))
            .stream()
            .map(outcome ->
                new FilterOption(outcome.getOutcomeTypeId(), outcome.getName())
            )
            .toList();
    }

    public Page<DocketEntryRow> findDocketEntries(
        UUID caseId,
        String query,
        String activityType,
        Pageable pageable
    ) {
        ensureCaseExists(caseId);

        Query mongoQuery = buildDocketQuery(caseId, query, activityType);
        long total = mongoTemplate.count(mongoQuery, Docket.class);

        mongoQuery
            .with(Sort.by(Sort.Direction.DESC, "timestamp"))
            .skip(pageable.getOffset())
            .limit(pageable.getPageSize());

        List<Docket> entries = mongoTemplate.find(mongoQuery, Docket.class);
        Map<UUID, String> filedByNames = findUserDisplayNames(
            entries
                .stream()
                .map(Docket::getPerformedById)
                .filter(id -> id != null)
                .collect(java.util.stream.Collectors.toSet())
        );

        List<DocketEntryRow> rows = entries
            .stream()
            .map(entry -> toDocketEntryRow(entry, filedByNames))
            .toList();

        return new PageImpl<>(rows, pageable, total);
    }

    public List<DocketActivityTypeOption> findDocketActivityTypeOptions(
        UUID caseId
    ) {
        ensureCaseExists(caseId);

        Set<String> types = new LinkedHashSet<>(MANUAL_DOCKET_ACTIVITY_TYPES);
        mongoTemplate
            .find(Query.query(Criteria.where("caseId").is(caseId)), Docket.class)
            .stream()
            .map(Docket::getActivityType)
            .filter(type -> type != null && !type.isBlank())
            .forEach(types::add);

        return types
            .stream()
            .map(type -> new DocketActivityTypeOption(type, prettify(type)))
            .toList();
    }

    public void createDocketEntry(
        UUID caseId,
        CreateDocketEntryForm form,
        UUID performedById
    ) {
        ensureCaseExists(caseId);

        String activityType = form.getActivityType() == null
            ? null
            : form.getActivityType().trim();
        if (activityType == null || activityType.isBlank()) {
            throw new DocketEntryException(
                "activityType",
                "Please select an activity type."
            );
        }
        if (!isKnownDocketActivityType(caseId, activityType)) {
            throw new DocketEntryException(
                "activityType",
                "Selected activity type does not exist."
            );
        }

        String description = form.getDescription() == null
            ? ""
            : form.getDescription().trim();
        if (description.isBlank()) {
            throw new DocketEntryException(
                "description",
                "Description is required."
            );
        }
        if (description.length() > 2000) {
            throw new DocketEntryException(
                "description",
                "Description must be 2000 characters or fewer."
            );
        }

        Docket docket = new Docket();
        docket.setCaseId(caseId);
        docket.setActivityType(activityType);
        docket.setDescription(description);
        docket.setPerformedById(performedById);
        docket.setTimestamp(Instant.now());
        mongoTemplate.save(docket);
    }

    @Transactional(readOnly = true)
    public List<JudgeOption> findActiveJudgeOptions() {
        return entityManager
            .createQuery(
                """
                SELECT new kh.edu.paragoniu.court_portal.cases.JudgeOption(
                    j.judgeId,
                    CONCAT('Hon. ', j.firstName, ' ', j.lastName)
                )
                FROM Judge j
                WHERE j.isActive = true
                ORDER BY j.firstName, j.lastName
                """,
                JudgeOption.class
            )
            .getResultList();
    }

    @Transactional(readOnly = true)
    @org.springframework.cache.annotation.Cacheable(value = "caseDetail", key = "#caseId")
    public CaseDetailView findDetail(UUID caseId) {
        CaseDetailProjection projection = entityManager
            .createQuery(
                """
                SELECT new kh.edu.paragoniu.court_portal.cases.CaseDetailProjection(
                    c.caseId,
                    c.caseNumber,
                    c.title,
                    c.description,
                    classification.name,
                    status.name,
                    c.filedAt,
                    c.lastUpdatedAt,
                    c.closedAt,
                    c.isPublic
                )
                FROM Case c
                LEFT JOIN c.classification classification
                LEFT JOIN c.status status
                WHERE c.caseId = :caseId
                """,
                CaseDetailProjection.class
            )
            .setParameter("caseId", caseId)
            .getResultStream()
            .findFirst()
            .orElseThrow(() -> new CaseDetailNotFoundException(caseId));

        return toDetailView(
            projection,
            findAssignedJudge(caseId),
            findAssignedGreffier(caseId)
        );
    }

    @Transactional(readOnly = true)
    public DispositionTabView findDispositionTab(UUID caseId) {
        ensureCaseExists(caseId);
        DispositionView disposition = dispositionRepository
            .findByCaseEntityCaseId(caseId)
            .map(this::toDispositionView)
            .orElse(null);

        List<Appeal> appeals = appealRepository.findByOriginalCaseCaseId(caseId);
        Appeal appeal = appeals.isEmpty() ? null : appeals.get(0);
        String appellateCaseNumber = "";
        String appellateCaseId = "";
        if (appeal != null && appeal.getNewCase() != null) {
            appellateCaseNumber = appeal.getNewCase().getCaseNumber();
            appellateCaseId = appeal.getNewCase().getCaseId().toString();
        }

        return new DispositionTabView(
            disposition,
            appeal != null,
            appellateCaseNumber,
            appellateCaseId
        );
    }

    @Transactional(readOnly = true)
    public CaseStatusSnapshot findStatusSnapshot(UUID caseId) {
        return entityManager
            .createQuery(
                """
                SELECT new kh.edu.paragoniu.court_portal.cases.CaseStatusSnapshot(
                    c.caseId,
                    c.caseNumber,
                    c.title,
                    status.statusId,
                    status.name
                )
                FROM Case c
                LEFT JOIN c.status status
                WHERE c.caseId = :caseId
                """,
                CaseStatusSnapshot.class
            )
            .setParameter("caseId", caseId)
            .getResultStream()
            .findFirst()
            .orElseThrow(() -> new CaseDetailNotFoundException(caseId));
    }

    @Transactional
    @org.springframework.cache.annotation.Caching(
        put = { @org.springframework.cache.annotation.CachePut(value = "caseDetail", key = "#caseId") },
        evict = {
            @org.springframework.cache.annotation.CacheEvict(value = "caseList", allEntries = true),
            @org.springframework.cache.annotation.CacheEvict(value = "publicCases", allEntries = true),
            @org.springframework.cache.annotation.CacheEvict(value = "publicCaseDetail", key = "#caseId")
        }
    )
    public CaseDetailView updateStatus(
        UUID caseId,
        Integer newStatusId,
        UUID performedById
    ) {
        if (newStatusId == null) {
            throw new StatusUpdateException(
                "statusId",
                "Please select a new status."
            );
        }

        CaseStatusSnapshot current = findStatusSnapshot(caseId);
        if (current.statusId() == null || current.statusName() == null) {
            throw new StatusUpdateException(
                null,
                "This case does not have a current status."
            );
        }
        if (current.statusId().equals(newStatusId)) {
            throw new StatusUpdateException(
                "statusId",
                "Select a different status."
            );
        }

        CaseStatus newStatus = entityManager.find(CaseStatus.class, newStatusId);
        if (newStatus == null) {
            throw new StatusUpdateException(
                "statusId",
                "Selected status does not exist."
            );
        }

        String newStatusName = findStatusName(newStatusId);
        validateSupportedStatus(newStatusName);

        Case caseEntity = caseRepository
            .findById(caseId)
            .orElseThrow(() -> new CaseDetailNotFoundException(caseId));
        Instant now = Instant.now();

        setField(caseEntity, "status", newStatus);
        setField(caseEntity, "lastUpdatedAt", now);
        if (isClosedStatus(newStatusName)) {
            setField(caseEntity, "closedAt", now);
        } else {
            setField(caseEntity, "closedAt", null);
        }

        caseRepository.save(caseEntity);
        createAutomaticDocketEntry(
            caseId,
            "STATUS_CHANGED",
            "Status changed from " +
            prettify(current.statusName()) +
            " to " +
            prettify(newStatusName) +
            ".",
            performedById,
            now
        );
        return findDetail(caseId);
    }

    @Transactional
    @org.springframework.cache.annotation.Caching(
        evict = {
            @org.springframework.cache.annotation.CacheEvict(value = "caseList", allEntries = true),
            @org.springframework.cache.annotation.CacheEvict(value = "publicCases", allEntries = true)
        }
    )
    public UUID createCase(CreateCaseForm form, UUID performedById) {
        CaseClassification classification = classificationRepository
            .findById(form.getClassificationId())
            .orElseThrow(() ->
                new CaseCreationException(
                    "classificationId",
                    "Selected classification does not exist."
                )
            );
        CaseStatus status = findInitialStatusReference();
        Judge judge = resolveJudge(form.getJudgeId());
        Instant now = Instant.now();

        Case caseEntity = new Case();
        String caseNumber = generateCaseNumber(form.getFiledDate());
        setField(caseEntity, "caseNumber", caseNumber);
        setField(caseEntity, "title", form.getTitle().trim());
        setField(caseEntity, "description", form.getDescription().trim());
        setField(caseEntity, "status", status);
        setField(caseEntity, "classification", classification);
        setField(caseEntity, "isPublic", form.isPublicCase());
        setField(
            caseEntity,
            "filedAt",
            form.getFiledDate().atStartOfDay(ZoneOffset.UTC).toInstant()
        );
        setField(caseEntity, "lastUpdatedAt", now);

        caseRepository.saveAndFlush(caseEntity);
        UUID caseId = findCaseId(caseNumber);

        if (judge != null) {
            CaseJudge caseJudge = new CaseJudge();
            CaseJudgeId caseJudgeId = new CaseJudgeId();
            setField(caseJudgeId, "caseId", caseId);
            setField(caseJudgeId, "judgeId", form.getJudgeId());
            setField(caseJudge, "id", caseJudgeId);
            setField(caseJudge, "caseEntity", caseEntity);
            setField(caseJudge, "judgeEntity", judge);
            setField(caseJudge, "isPresiding", true);
            setField(caseJudge, "assignedAt", now);
            caseJudgeRepository.save(caseJudge);
        }

        createAutomaticDocketEntry(
            caseId,
            "FILING",
            "Case " + caseNumber + " was registered and opened.",
            performedById,
            now
        );

        return caseId;
    }

    @Transactional
    @org.springframework.cache.annotation.Caching(
        evict = {
            @org.springframework.cache.annotation.CacheEvict(value = "caseList", allEntries = true),
            @org.springframework.cache.annotation.CacheEvict(value = "publicCases", allEntries = true),
            @org.springframework.cache.annotation.CacheEvict(value = "caseDetail", key = "#caseId"),
            @org.springframework.cache.annotation.CacheEvict(value = "publicCaseDetail", key = "#caseId")
        }
    )
    public UUID createDisposition(
        UUID caseId,
        CreateDispositionForm form,
        UUID performedById
    ) {
        if (dispositionRepository.findByCaseEntityCaseId(caseId).isPresent()) {
            throw new DispositionException(
                null,
                "This case already has a disposition."
            );
        }

        Case caseEntity = caseRepository
            .findById(caseId)
            .orElseThrow(() -> new CaseDetailNotFoundException(caseId));
        DispositionOutcome outcome = dispositionOutcomeRepository
            .findById(form.getOutcomeTypeId())
            .orElseThrow(() ->
                new DispositionException(
                    "outcomeTypeId",
                    "Selected outcome does not exist."
                )
            );
        Judge judge = findPresidingJudge(caseId);
        if (judge == null) {
            throw new DispositionException(
                null,
                "Assign a presiding judge before recording a disposition."
            );
        }

        Instant effectiveAt = form
            .getDispositionDate()
            .atStartOfDay(DISPLAY_ZONE)
            .toInstant();
        Instant now = Instant.now();

        Disposition disposition = new Disposition();
        disposition.setCaseEntity(caseEntity);
        disposition.setJudgeEntity(judge);
        disposition.setOutcomeType(outcome);
        disposition.setRulingDetails(form.getRulingSummary().trim());
        disposition.setEffectiveAt(effectiveAt);
        dispositionRepository.save(disposition);

        CaseStatus disposedStatus = findStatusReference("DISPOSED");
        setField(caseEntity, "status", disposedStatus);
        setField(caseEntity, "closedAt", effectiveAt);
        setField(caseEntity, "lastUpdatedAt", now);
        caseRepository.save(caseEntity);

        createAutomaticDocketEntry(
            caseId,
            "DISPOSITION",
            "Disposition recorded: " + outcome.getName() + ".",
            performedById,
            now
        );

        return disposition.getDispositionId();
    }

    @Transactional
    @org.springframework.cache.annotation.Caching(
        evict = {
            @org.springframework.cache.annotation.CacheEvict(value = "caseList", allEntries = true),
            @org.springframework.cache.annotation.CacheEvict(value = "publicCases", allEntries = true),
            @org.springframework.cache.annotation.CacheEvict(value = "caseDetail", key = "#originalCaseId"),
            @org.springframework.cache.annotation.CacheEvict(value = "publicCaseDetail", key = "#originalCaseId")
        }
    )
    public UUID initiateAppeal(UUID originalCaseId, UUID performedById) {
        Disposition disposition = dispositionRepository
            .findByCaseEntityCaseId(originalCaseId)
            .orElseThrow(() ->
                new DispositionException(
                    null,
                    "Record a disposition before initiating an appeal."
                )
            );
        if (!appealRepository.findByOriginalCaseCaseId(originalCaseId).isEmpty()) {
            throw new DispositionException(
                null,
                "This case already has an appeal."
            );
        }

        Case originalCase = disposition.getCaseEntity();
        Instant now = Instant.now();
        LocalDate filedDate = LocalDate.now(DISPLAY_ZONE);
        String appellateCaseNumber = generateCaseNumber(filedDate);

        Case appellateCase = new Case();
        setField(appellateCase, "caseNumber", appellateCaseNumber);
        setField(
            appellateCase,
            "title",
            "Appeal of " +
            originalCase.getCaseNumber() +
            ": " +
            originalCase.getTitle()
        );
        setField(
            appellateCase,
            "description",
            "Appellate case created from " +
            originalCase.getCaseNumber() +
            ". " +
            originalCase.getDescription()
        );
        setField(appellateCase, "status", findStatusReference("UNDER_APPEAL"));
        setField(appellateCase, "classification", originalCase.getClassification());
        setField(appellateCase, "isPublic", originalCase.isPublic());
        setField(
            appellateCase,
            "filedAt",
            filedDate.atStartOfDay(DISPLAY_ZONE).toInstant()
        );
        setField(appellateCase, "lastUpdatedAt", now);
        caseRepository.saveAndFlush(appellateCase);

        Appeal appeal = new Appeal();
        appeal.setOriginalCase(originalCase);
        appeal.setNewCase(appellateCase);
        appeal.setStatus("INITIATED");
        appealRepository.save(appeal);

        createAutomaticDocketEntry(
            originalCaseId,
            "APPEAL",
            "Appeal initiated. Appellate case " +
            appellateCaseNumber +
            " was created.",
            performedById,
            now
        );
        createAutomaticDocketEntry(
            appellateCase.getCaseId(),
            "FILING",
            "Appellate case " +
            appellateCaseNumber +
            " was created from " +
            originalCase.getCaseNumber() +
            ".",
            performedById,
            now
        );

        return appellateCase.getCaseId();
    }

    private CaseStatus findInitialStatusReference() {
        Integer statusId = entityManager
            .createQuery(
                """
                SELECT s.statusId
                FROM CaseStatus s
                WHERE s.name = :statusName
                """,
                Integer.class
            )
            .setParameter("statusName", "FILING_OPEN")
            .getResultStream()
            .findFirst()
            .orElseThrow(() ->
                new CaseCreationException(
                    null,
                    "Initial case status FILING_OPEN is missing."
                )
            );
        return entityManager.getReference(CaseStatus.class, statusId);
    }

    private CaseStatus findStatusReference(String statusName) {
        Integer statusId = entityManager
            .createQuery(
                """
                SELECT s.statusId
                FROM CaseStatus s
                WHERE s.name = :statusName
                """,
                Integer.class
            )
            .setParameter("statusName", statusName)
            .getResultStream()
            .findFirst()
            .orElseThrow(() ->
                new DispositionException(
                    null,
                    "Case status " + statusName + " is missing."
                )
            );
        return entityManager.getReference(CaseStatus.class, statusId);
    }

    private Judge resolveJudge(UUID judgeId) {
        if (judgeId == null) {
            return null;
        }

        Boolean active = entityManager
            .createQuery(
                """
                SELECT j.isActive
                FROM Judge j
                WHERE j.judgeId = :judgeId
                """,
                Boolean.class
            )
            .setParameter("judgeId", judgeId)
            .getResultStream()
            .findFirst()
            .orElseThrow(() ->
                new CaseCreationException(
                    "judgeId",
                    "Selected judge does not exist."
                )
            );
        if (!active) {
            throw new CaseCreationException(
                "judgeId",
                "Selected judge is not active."
            );
        }
        return entityManager.getReference(Judge.class, judgeId);
    }

    private String findStatusName(Integer statusId) {
        return entityManager
            .createQuery(
                """
                SELECT s.name
                FROM CaseStatus s
                WHERE s.statusId = :statusId
                """,
                String.class
            )
            .setParameter("statusId", statusId)
            .getResultStream()
            .findFirst()
            .orElseThrow(() ->
                new StatusUpdateException(
                    "statusId",
                    "Selected status does not exist."
                )
            );
    }

    private void validateSupportedStatus(String statusName) {
        if (
            !List.of(
                "DRAFT",
                "FILING_OPEN",
                "SCHEDULED",
                "IN_TRIAL",
                "ADJOURNED",
                "DISPOSED",
                "UNDER_APPEAL"
            )
                .contains(statusName)
        ) {
            throw new StatusUpdateException(
                "statusId",
                "Selected status is not supported."
            );
        }
    }

    private boolean isClosedStatus(String statusName) {
        return "DISPOSED".equals(statusName);
    }

    private void createAutomaticDocketEntry(
        UUID caseId,
        String activityType,
        String description,
        UUID performedById,
        Instant timestamp
    ) {
        Docket docket = new Docket();
        docket.setCaseId(caseId);
        docket.setActivityType(activityType);
        docket.setDescription(description);
        docket.setPerformedById(performedById);
        docket.setTimestamp(timestamp);
        mongoTemplate.save(docket);
    }

    private Query buildDocketQuery(
        UUID caseId,
        String query,
        String activityType
    ) {
        List<Criteria> filters = new ArrayList<>();
        filters.add(Criteria.where("caseId").is(caseId));

        if (query != null && !query.isBlank()) {
            String escaped = Pattern.quote(query.trim());
            filters.add(
                new Criteria()
                    .orOperator(
                        Criteria.where("description")
                            .regex(escaped, "i"),
                        Criteria.where("activityType")
                            .regex(escaped, "i")
                    )
            );
        }

        if (activityType != null && !activityType.isBlank()) {
            filters.add(Criteria.where("activityType").is(activityType.trim()));
        }

        return new Query(new Criteria().andOperator(filters));
    }

    private boolean isKnownDocketActivityType(
        UUID caseId,
        String activityType
    ) {
        if (MANUAL_DOCKET_ACTIVITY_TYPES.contains(activityType)) {
            return true;
        }
        return mongoTemplate.exists(
            Query.query(
                Criteria.where("caseId")
                    .is(caseId)
                    .and("activityType")
                    .is(activityType)
            ),
            Docket.class
        );
    }

    private Map<UUID, String> findUserDisplayNames(Set<UUID> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }

        List<Object[]> rows = entityManager
            .createQuery(
                """
                SELECT u.userId, u.firstName, u.lastName
                FROM User u
                WHERE u.userId IN :userIds
                """,
                Object[].class
            )
            .setParameter("userIds", userIds)
            .getResultList();

        Map<UUID, String> names = new HashMap<>();
        for (Object[] row : rows) {
            UUID userId = (UUID) row[0];
            String name = formatPersonName("", (String) row[1], (String) row[2]);
            names.put(userId, name.isBlank() ? "Unknown user" : name);
        }
        return names;
    }

    private DocketEntryRow toDocketEntryRow(
        Docket docket,
        Map<UUID, String> filedByNames
    ) {
        UUID performedById = docket.getPerformedById();
        String filedBy = performedById == null
            ? "System"
            : filedByNames.getOrDefault(performedById, "Unknown user");

        return new DocketEntryRow(
            formatDate(docket.getTimestamp()),
            prettify(docket.getActivityType()),
            docketBadgeClass(docket.getActivityType()),
            docket.getDescription(),
            filedBy
        );
    }

    private void ensureCaseExists(UUID caseId) {
        if (!caseRepository.existsById(caseId)) {
            throw new CaseDetailNotFoundException(caseId);
        }
    }

    private AssignedJudgeView findAssignedJudge(UUID caseId) {
        return entityManager
            .createQuery(
                """
                SELECT new kh.edu.paragoniu.court_portal.cases.AssignedJudgeProjection(
                    j.firstName,
                    j.lastName,
                    j.licenseNumber
                )
                FROM CaseJudge cj
                JOIN cj.judgeEntity j
                WHERE cj.id.caseId = :caseId
                ORDER BY CASE WHEN cj.isPresiding = true THEN 0 ELSE 1 END,
                    cj.assignedAt DESC
                """,
                AssignedJudgeProjection.class
            )
            .setParameter("caseId", caseId)
            .setMaxResults(1)
            .getResultStream()
            .findFirst()
            .map(this::toJudgeView)
            .orElseGet(AssignedJudgeView::unassigned);
    }

    private Judge findPresidingJudge(UUID caseId) {
        return entityManager
            .createQuery(
                """
                SELECT j
                FROM CaseJudge cj
                JOIN cj.judgeEntity j
                WHERE cj.id.caseId = :caseId
                ORDER BY CASE WHEN cj.isPresiding = true THEN 0 ELSE 1 END,
                    cj.assignedAt DESC
                """,
                Judge.class
            )
            .setParameter("caseId", caseId)
            .setMaxResults(1)
            .getResultStream()
            .findFirst()
            .orElse(null);
    }

    private AssignedGreffierView findAssignedGreffier(UUID caseId) {
        return entityManager
            .createQuery(
                """
                SELECT new kh.edu.paragoniu.court_portal.cases.AssignedGreffierProjection(
                    greffier.firstName,
                    greffier.lastName,
                    role.name,
                    assignment.assignedAt
                )
                FROM CaseAssignment assignment
                JOIN assignment.greffierEntity greffier
                LEFT JOIN UserRole userRole
                    ON userRole.id.userId = greffier.userId
                LEFT JOIN userRole.systemRole role
                WHERE assignment.caseEntity.caseId = :caseId
                ORDER BY assignment.assignedAt DESC,
                    greffier.firstName,
                    greffier.lastName,
                    role.name
                """,
                AssignedGreffierProjection.class
            )
            .setParameter("caseId", caseId)
            .setMaxResults(1)
            .getResultStream()
            .findFirst()
            .map(this::toGreffierView)
            .orElseGet(AssignedGreffierView::unassigned);
    }

    private String generateCaseNumber(LocalDate filedDate) {
        int year = filedDate.getYear();
        long nextNumber =
            entityManager
                .createQuery(
                    """
                    SELECT COUNT(c)
                    FROM Case c
                    WHERE c.caseNumber LIKE :prefix
                    """,
                    Long.class
                )
                .setParameter("prefix", "CMS-" + year + "-%")
                .getSingleResult() +
            1;

        String caseNumber;
        do {
            caseNumber = "CMS-" + year + "-" + String.format("%04d", nextNumber++);
        } while (caseRepository.findByCaseNumber(caseNumber).isPresent());

        return caseNumber;
    }

    private UUID findCaseId(String caseNumber) {
        return entityManager
            .createQuery(
                """
                SELECT c.caseId
                FROM Case c
                WHERE c.caseNumber = :caseNumber
                """,
                UUID.class
            )
            .setParameter("caseNumber", caseNumber)
            .getSingleResult();
    }

    private CaseDetailView toDetailView(
        CaseDetailProjection projection,
        AssignedJudgeView assignedJudge,
        AssignedGreffierView assignedGreffier
    ) {
        String status = projection.statusName();

        return new CaseDetailView(
            projection.caseId(),
            projection.caseNumber(),
            projection.title(),
            projection.description(),
            projection.classificationName() == null
                ? "Unknown"
                : projection.classificationName(),
            prettify(status),
            badgeClass(status),
            formatDate(projection.filedAt()),
            formatDate(projection.lastUpdatedAt()),
            formatDate(projection.closedAt()),
            projection.publicCase() ? "Public" : "Internal",
            assignedJudge,
            assignedGreffier
        );
    }

    private AssignedJudgeView toJudgeView(AssignedJudgeProjection projection) {
        String name = formatPersonName(
            "Hon.",
            projection.firstName(),
            projection.lastName()
        );
        if (name.isBlank()) {
            return AssignedJudgeView.unassigned();
        }
        return new AssignedJudgeView(
            name,
            projection.licenseNumber() == null
                ? ""
                : projection.licenseNumber()
        );
    }

    private AssignedGreffierView toGreffierView(
        AssignedGreffierProjection projection
    ) {
        String name = formatPersonName(
            "",
            projection.firstName(),
            projection.lastName()
        );
        if (name.isBlank()) {
            return AssignedGreffierView.unassigned();
        }
        return new AssignedGreffierView(
            name,
            prettify(projection.roleName()),
            formatDate(projection.assignedAt())
        );
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(
                "Unable to set shared entity field: " + fieldName,
                ex
            );
        }
    }

    private CaseRow toRow(CaseRowProjection projection) {
        String status = projection.statusName();

        return new CaseRow(
            projection.caseId(),
            projection.caseNumber(),
            projection.title(),
            projection.classificationName(),
            prettify(status),
            badgeClass(status),
            formatDate(projection.filedAt()),
            formatJudgeName(projection.judgeFirstName(), projection.judgeLastName())
        );
    }

    private DispositionView toDispositionView(Disposition disposition) {
        return new DispositionView(
            disposition.getDispositionId(),
            disposition.getOutcomeType().getName(),
            formatDate(disposition.getEffectiveAt()),
            disposition.getRulingDetails(),
            formatJudgeName(
                disposition.getJudgeEntity().getFirstName(),
                disposition.getJudgeEntity().getLastName()
            )
        );
    }

    private String formatJudgeName(String firstName, String lastName) {
        String fullName = formatPersonName("", firstName, lastName);
        return fullName.isBlank() ? "Unassigned" : "Hon. " + fullName;
    }

    private String formatPersonName(
        String prefix,
        String firstName,
        String lastName
    ) {
        String fullName = (
            (firstName == null ? "" : firstName.trim()) +
            " " +
            (lastName == null ? "" : lastName.trim())
        ).trim();

        if (fullName.isBlank()) {
            return "";
        }
        return prefix == null || prefix.isBlank()
            ? fullName
            : prefix + " " + fullName;
    }

    private String formatDate(Instant instant) {
        return instant == null ? "" : DATE_FMT.format(instant);
    }

    public String formatCurrentTimestamp() {
        return DATE_TIME_FMT.format(Instant.now());
    }

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

    private String docketBadgeClass(String activityType) {
        return switch (activityType) {
            case "FILING" -> "badge--blue";
            case "EVIDENCE" -> "badge--amber";
            case "HEARING" -> "badge--gray";
            case "STATUS_CHANGED" -> "badge--indigo";
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

    private record QueryParams(
        String query,
        Integer classificationId,
        Integer statusId,
        Instant filedFrom,
        Instant filedTo
    ) implements java.io.Serializable {
        static QueryParams from(
            String query,
            Integer classificationId,
            Integer statusId,
            LocalDate filedFrom,
            LocalDate filedTo
        ) {
            return new QueryParams(
                query == null || query.isBlank() ? null : query.trim(),
                classificationId,
                statusId,
                filedFrom == null
                    ? null
                    : filedFrom.atStartOfDay(ZoneOffset.UTC).toInstant(),
                filedTo == null
                    ? null
                    : filedTo.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant()
            );
        }

        String whereClause() {
            List<String> filters = new ArrayList<>();
            if (query != null) {
                filters.add(
                    """
                    (LOWER(c.caseNumber) LIKE LOWER(CONCAT('%', :query, '%'))
                        OR LOWER(c.title) LIKE LOWER(CONCAT('%', :query, '%')))
                    """
                );
            }
            if (classificationId != null) {
                filters.add(
                    "c.classification.classificationId = :classificationId"
                );
            }
            if (statusId != null) {
                filters.add("c.status.statusId = :statusId");
            }
            if (filedFrom != null) {
                filters.add("c.filedAt >= :filedFrom");
            }
            if (filedTo != null) {
                filters.add("c.filedAt < :filedTo");
            }

            return filters.isEmpty()
                ? ""
                : " WHERE " + String.join(" AND ", filters);
        }

        void applyTo(jakarta.persistence.Query jpaQuery) {
            if (query != null) {
                jpaQuery.setParameter("query", query);
            }
            if (classificationId != null) {
                jpaQuery.setParameter("classificationId", classificationId);
            }
            if (statusId != null) {
                jpaQuery.setParameter("statusId", statusId);
            }
            if (filedFrom != null) {
                jpaQuery.setParameter("filedFrom", filedFrom);
            }
            if (filedTo != null) {
                jpaQuery.setParameter("filedTo", filedTo);
            }
        }
    }
}
