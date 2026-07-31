package kh.edu.paragoniu.court_portal.cases;

import jakarta.persistence.EntityManager;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import kh.edu.paragoniu.court_shared.entity.Docket;
import kh.edu.paragoniu.court_shared.entity.Documents;
import kh.edu.paragoniu.court_shared.entity.Judge;
import kh.edu.paragoniu.court_shared.repository.CaseRepository;
import kh.edu.paragoniu.court_shared.repository.DispositionRepository;
import kh.edu.paragoniu.court_shared.repository.HearingRepository;
import kh.edu.paragoniu.court_shared.repository.JudgeRepository;
import kh.edu.paragoniu.court_shared.repository.UserRepository;
import kh.edu.paragoniu.court_shared.service.S3Service;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class DocumentService {

    /** Shared with participants/ParticipantDirectoryService's Documents tab cache. */
    private static final String PARTICIPANTS_DIRECTORY_CACHE = "participantsDirectory";

    private static final ZoneId DISPLAY_ZONE = ZoneId.of("Asia/Phnom_Penh");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter
        .ofPattern("MMM dd, yyyy", Locale.ENGLISH)
        .withZone(DISPLAY_ZONE);
    private static final DateTimeFormatter DATE_TIME_FMT = DateTimeFormatter
        .ofPattern("MMM dd, yyyy, hh:mm a", Locale.ENGLISH)
        .withZone(DISPLAY_ZONE);
    private static final long MAX_FILE_SIZE = 20L * 1024L * 1024L;
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
        "pdf",
        "docx",
        "jpg",
        "jpeg"
    );

    private static final List<DocumentTypeOption> DOCUMENT_TYPES = List.of(
        new DocumentTypeOption("Filing", "Filing"),
        new DocumentTypeOption("Motion", "Motion"),
        new DocumentTypeOption("Continuance", "Continuance"),
        new DocumentTypeOption("Evidence", "Evidence"),
        new DocumentTypeOption("Disposition", "Disposition"),
        new DocumentTypeOption("Appeal", "Appeal")
    );
    private static final List<DocumentTypeOption> MOTION_STATUSES = List.of(
        new DocumentTypeOption("Pending", "Pending"),
        new DocumentTypeOption("Approved", "Approved"),
        new DocumentTypeOption("Denied", "Denied"),
        new DocumentTypeOption("Moot", "Moot")
    );

    private final CaseRepository caseRepository;
    private final JudgeRepository judgeRepository;
    private final HearingRepository hearingRepository;
    private final DispositionRepository dispositionRepository;
    private final UserRepository userRepository;
    private final EntityManager entityManager;
    private final MongoTemplate mongoTemplate;
    private final ObjectProvider<S3Service> s3ServiceProvider;
    private final CacheManager cacheManager;

    public DocumentService(
        CaseRepository caseRepository,
        JudgeRepository judgeRepository,
        HearingRepository hearingRepository,
        DispositionRepository dispositionRepository,
        UserRepository userRepository,
        EntityManager entityManager,
        MongoTemplate mongoTemplate,
        ObjectProvider<S3Service> s3ServiceProvider,
        CacheManager cacheManager
    ) {
        this.caseRepository = caseRepository;
        this.judgeRepository = judgeRepository;
        this.hearingRepository = hearingRepository;
        this.dispositionRepository = dispositionRepository;
        this.userRepository = userRepository;
        this.entityManager = entityManager;
        this.mongoTemplate = mongoTemplate;
        this.s3ServiceProvider = s3ServiceProvider;
        this.cacheManager = cacheManager;
    }

    /**
     * Evicts participants/ParticipantDirectoryService's Documents tab cache,
     * since a new/edited document can change what a participant's Documents
     * tab shows. Unlike the JPA writes elsewhere in this codebase, this isn't
     * wrapped in @Transactional - createDocument/updateMotionStatus write
     * directly to Mongo via MongoTemplate.save, which is not deferred the way
     * Hibernate's flush-at-commit is, so there's no pre-commit staleness
     * window to guard against here; a synchronous clear right after the save
     * returns is safe (see ParticipantDirectoryService for the JPA case,
     * where the afterCommit callback is actually needed).
     */
    private void evictParticipantsDirectoryCache() {
        Cache cache = cacheManager.getCache(PARTICIPANTS_DIRECTORY_CACHE);
        if (cache != null) {
            cache.clear();
        }
    }

    public Page<DocumentRow> findDocuments(
        UUID caseId,
        String query,
        String documentType,
        LocalDate uploadedDate,
        Pageable pageable
    ) {
        ensureCaseExists(caseId);
        Query mongoQuery = buildDocumentQuery(
            caseId,
            query,
            documentType,
            uploadedDate
        );
        long total = mongoTemplate.count(mongoQuery, Documents.class);
        mongoQuery
            .with(pageable)
            .with(org.springframework.data.domain.Sort.by(
                org.springframework.data.domain.Sort.Direction.DESC,
                "uploaded_at"
            ));

        List<DocumentRow> rows = mongoTemplate
            .find(mongoQuery, Documents.class)
            .stream()
            .map(this::toRow)
            .toList();
        return new PageImpl<>(rows, pageable, total);
    }

    public long countDocuments(UUID caseId) {
        ensureCaseExists(caseId);
        return mongoTemplate.count(
            Query.query(Criteria.where("caseId").is(caseId)),
            Documents.class
        );
    }

    public List<DocumentTypeOption> findDocumentTypes() {
        return DOCUMENT_TYPES;
    }

    public List<DocumentTypeOption> findMotionStatuses() {
        return MOTION_STATUSES;
    }

    public List<DocumentOption> findMotionOptions(UUID caseId) {
        ensureCaseExists(caseId);
        return mongoTemplate
            .find(
                Query.query(
                    Criteria.where("caseId")
                        .is(caseId)
                        .and("documentType")
                        .is("Motion")
                        .and("metadata.status")
                        .is("Approved")
                ),
                Documents.class
            )
            .stream()
            .map(document -> new DocumentOption(document.getId(), document.getTitle()))
            .toList();
    }

    public List<DocumentOption> findHearingOptions(UUID caseId) {
        ensureCaseExists(caseId);
        return hearingRepository
            .findByCaseEntityCaseId(caseId)
            .stream()
            .map(hearing ->
                new DocumentOption(
                    hearing.getHearingId().toString(),
                    hearing.getHearingType().getName() +
                    " - " +
                    DATE_TIME_FMT.format(hearing.getStartAt())
                )
            )
            .toList();
    }

    public List<DocumentOption> findDispositionOptions(UUID caseId) {
        ensureCaseExists(caseId);
        return entityManager
            .createQuery(
                """
                SELECT d.dispositionId, d.outcomeType.name, d.effectiveAt
                FROM Disposition d
                WHERE d.caseEntity.caseId = :caseId
                ORDER BY d.effectiveAt DESC
                """,
                Object[].class
            )
            .setParameter("caseId", caseId)
            .getResultStream()
            .map(row ->
                new DocumentOption(
                    row[0].toString(),
                    row[1] + " - " + DATE_FMT.format((Instant) row[2])
                )
            )
            .toList();
    }

    @Cacheable(value = "refData", key = "'docJudgeOptions'")
    public List<DocumentOption> findJudgeOptions() {
        return judgeRepository
            .findByIsActiveTrue()
            .stream()
            .map(judge ->
                new DocumentOption(
                    judge.getJudgeId().toString(),
                    formatJudgeName(judge)
                )
            )
            .toList();
    }

    @Cacheable(value = "refData", key = "'handlerOptions'")
    public List<DocumentOption> findHandlerOptions() {
        return userRepository
            .findAll()
            .stream()
            .map(user ->
                new DocumentOption(
                    user.getUserId().toString(),
                    user.getFirstName() + " " + user.getLastName()
                )
            )
            .toList();
    }

    public String createDocument(
        UUID caseId,
        UploadDocumentForm form,
        UUID submittedById
    ) {
        ensureCaseExists(caseId);
        String documentType = normalizeDocumentType(form.getDocumentType());
        MultipartFile file = form.getFile();
        validateFile(file);

        Map<String, Object> metadata = metadataFor(caseId, documentType, form);
        S3Service s3Service = resolveS3Service();
        String storageKey;
        try {
            storageKey = s3Service.uploadFile(
                "documents/" + caseId,
                Objects.requireNonNull(file.getOriginalFilename()),
                file.getBytes(),
                file.getContentType()
            );
        } catch (IOException | RuntimeException ex) {
            throw new DocumentException(
                "file",
                "The file could not be uploaded. Please try again."
            );
        }

        Documents document = new Documents();
        document.setCaseId(caseId);
        document.setDocumentType(documentType);
        document.setTitle(form.getTitle().trim());
        document.setSubmittedById(submittedById);
        document.setFilePath(storageKey);
        document.setConfidential(form.isConfidential());
        document.setUploadedAt(Instant.now());
        document.setMetadata(metadata);
        mongoTemplate.save(document);
        evictParticipantsDirectoryCache();
        createAutomaticDocketEntry(
            caseId,
            documentType.toUpperCase(Locale.ENGLISH),
            "Document uploaded: " + documentType + " - " + document.getTitle() + ".",
            submittedById,
            document.getUploadedAt()
        );
        return document.getId();
    }

    public DocumentDetailView findDocument(UUID caseId, String documentId) {
        ensureCaseExists(caseId);
        Documents document = mongoTemplate.findById(documentId, Documents.class);
        if (document == null) {
            throw new DocumentException(null, "Document was not found.");
        }
        if (!caseId.equals(document.getCaseId())) {
            throw new DocumentException(null, "Document was not found.");
        }

        Map<String, Object> metadata = document.getMetadata() == null
            ? Map.of()
            : document.getMetadata();
        Map<String, Object> displayMetadata = displayMetadata(metadata);
        return new DocumentDetailView(
            document.getId(),
            document.getCaseId().toString(),
            findCaseNumber(caseId),
            document.getTitle(),
            document.getDocumentType(),
            prettify(document.getDocumentType()),
            badgeClass(document.getDocumentType()),
            findUserDisplayName(document.getSubmittedById()),
            formatDateTime(document.getUploadedAt()),
            document.isConfidential(),
            generateFileUrl(document.getFilePath()),
            document.getFilePath(),
            displayMetadata,
            chainOfCustody(displayMetadata)
        );
    }

    public String findDownloadUrl(UUID caseId, String documentId) {
        return findDocument(caseId, documentId).fileUrl();
    }

    public UpdateMotionStatusForm findMotionStatusForm(
        UUID caseId,
        String documentId
    ) {
        Documents document = findMotionDocument(caseId, documentId);
        Map<String, Object> metadata = document.getMetadata() == null
            ? Map.of()
            : document.getMetadata();

        UpdateMotionStatusForm form = new UpdateMotionStatusForm();
        form.setStatus(
            metadata.get("status") == null
                ? "Pending"
                : metadata.get("status").toString()
        );
        Object ruledByJudgeId = metadata.get("ruled_by_judge_id");
        form.setRuledByJudgeId(
            ruledByJudgeId == null ? "" : ruledByJudgeId.toString()
        );
        Object ruledAt = metadata.get("ruled_at");
        form.setRuledAt(ruledAt == null ? "" : ruledAt.toString());
        return form;
    }

    public void updateMotionStatus(
        UUID caseId,
        String documentId,
        UpdateMotionStatusForm form,
        UUID performedById
    ) {
        Documents document = findMotionDocument(caseId, documentId);
        Map<String, Object> currentMetadata = document.getMetadata() == null
            ? Map.of()
            : document.getMetadata();
        String oldStatus = currentMetadata.get("status") == null
            ? "Pending"
            : currentMetadata.get("status").toString();
        String newStatus = normalizeMotionStatus(form.getStatus());
        Map<String, Object> metadata = document.getMetadata() == null
            ? new LinkedHashMap<>()
            : new LinkedHashMap<>(document.getMetadata());

        metadata.put("status", newStatus);
        metadata.put(
            "ruled_by_judge_id",
            optionalUuid(form.getRuledByJudgeId(), "ruledByJudgeId")
        );
        metadata.put("ruled_at", trim(form.getRuledAt()));

        document.setMetadata(metadata);
        mongoTemplate.save(document);
        evictParticipantsDirectoryCache();
        if (!oldStatus.equals(newStatus)) {
            createAutomaticDocketEntry(
                caseId,
                "MOTION",
                "Motion status updated from " +
                oldStatus +
                " to " +
                newStatus +
                " for " +
                document.getTitle() +
                ".",
                performedById,
                Instant.now()
            );
        }
    }

    private Query buildDocumentQuery(
        UUID caseId,
        String query,
        String documentType,
        LocalDate uploadedDate
    ) {
        List<Criteria> filters = new ArrayList<>();
        filters.add(Criteria.where("caseId").is(caseId));

        if (query != null && !query.isBlank()) {
            filters.add(
                Criteria.where("title").regex(Pattern.quote(query.trim()), "i")
            );
        }
        if (documentType != null && !documentType.isBlank()) {
            filters.add(Criteria.where("documentType").is(normalizeDocumentType(documentType)));
        }
        if (uploadedDate != null) {
            filters.add(
                Criteria.where("uploadedAt")
                    .gte(uploadedDate.atStartOfDay(DISPLAY_ZONE).toInstant())
                    .lt(uploadedDate.plusDays(1).atStartOfDay(DISPLAY_ZONE).toInstant())
            );
        }

        return new Query(new Criteria().andOperator(filters));
    }

    private S3Service resolveS3Service() {
        S3Service service = s3ServiceProvider.getIfAvailable();
        if (service == null) {
            throw new DocumentException(
                "file",
                "Document storage is not configured. Please check R2/S3 settings."
            );
        }
        return service;
    }

    private String generateFileUrl(String filePath) {
        S3Service service = s3ServiceProvider.getIfAvailable();
        return service == null ? null : service.generatePublicUrl(filePath);
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

    private DocumentRow toRow(Documents document) {
        return new DocumentRow(
            document.getId(),
            document.getDocumentType(),
            prettify(document.getDocumentType()),
            badgeClass(document.getDocumentType()),
            document.getTitle(),
            formatDate(document.getUploadedAt()),
            document.isConfidential()
        );
    }

    private void ensureCaseExists(UUID caseId) {
        if (!caseRepository.existsById(caseId)) {
            throw new CaseDetailNotFoundException(caseId);
        }
    }

    private String findCaseNumber(UUID caseId) {
        return caseRepository
            .findById(caseId)
            .map(kh.edu.paragoniu.court_shared.entity.Case::getCaseNumber)
            .orElse("");
    }

    private String normalizeDocumentType(String documentType) {
        return DOCUMENT_TYPES
            .stream()
            .filter(type -> type.code().equalsIgnoreCase(documentType))
            .map(DocumentTypeOption::code)
            .findFirst()
            .orElseThrow(() ->
                new DocumentException(
                    "documentType",
                    "Selected document type is not supported."
                )
            );
    }

    private String normalizeMotionStatus(String status) {
        return MOTION_STATUSES
            .stream()
            .filter(type -> type.code().equalsIgnoreCase(status))
            .map(DocumentTypeOption::code)
            .findFirst()
            .orElseThrow(() ->
                new DocumentException(
                    "status",
                    "Selected motion status is not supported."
                )
            );
    }

    private Documents findMotionDocument(UUID caseId, String documentId) {
        ensureCaseExists(caseId);
        Documents document = mongoTemplate.findById(documentId, Documents.class);
        if (
            document == null ||
            !caseId.equals(document.getCaseId()) ||
            !"Motion".equals(document.getDocumentType())
        ) {
            throw new DocumentException(null, "Motion document was not found.");
        }
        return document;
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new DocumentException("file", "Upload file is required.");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new DocumentException(
                "file",
                "Upload file must be 20 MB or smaller."
            );
        }
        String filename = file.getOriginalFilename();
        String extension = filename == null || !filename.contains(".")
            ? ""
            : filename.substring(filename.lastIndexOf('.') + 1).toLowerCase(Locale.ENGLISH);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new DocumentException(
                "file",
                "Only PDF, DOCX, JPG, or JPEG files are allowed."
            );
        }
    }

    private Map<String, Object> metadataFor(
        UUID caseId,
        String documentType,
        UploadDocumentForm form
    ) {
        return switch (documentType) {
            case "Filing" -> filingMetadata(form);
            case "Motion" -> motionMetadata(form);
            case "Continuance" -> continuanceMetadata(caseId, form);
            case "Evidence" -> evidenceMetadata(form);
            case "Disposition" -> dispositionMetadata(caseId, form);
            case "Appeal" -> appealMetadata(caseId, form);
            default -> Map.of();
        };
    }

    private Map<String, Object> filingMetadata(UploadDocumentForm form) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("filing_category", trim(form.getFilingCategory()));
        metadata.put("relief_sought", trim(form.getReliefSought()));
        return metadata;
    }

    private Map<String, Object> motionMetadata(UploadDocumentForm form) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("request_action", trim(form.getRequestAction()));
        metadata.put("status", "Pending");
        metadata.put("argument_summary", trim(form.getArgumentSummary()));
        metadata.put("hearing_required", form.isHearingRequired());
        metadata.put("ruled_by_judge_id", null);
        metadata.put("ruled_at", null);
        return metadata;
    }

    private Map<String, Object> continuanceMetadata(
        UUID caseId,
        UploadDocumentForm form
    ) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("approved_motion_id", existingMotionId(caseId, form.getApprovedMotionId()));
        metadata.put("original_hearing_id", existingHearingId(caseId, form.getOriginalHearingId(), "originalHearingId"));
        metadata.put("new_hearing_id", existingHearingId(caseId, form.getNewHearingId(), "newHearingId"));
        metadata.put("reason", trim(form.getContinuanceReason()));
        metadata.put("requested_by_party", trim(form.getRequestedByParty()));
        return metadata;
    }

    private Map<String, Object> evidenceMetadata(UploadDocumentForm form) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("evidence_type", trim(form.getEvidenceType()));
        metadata.put("exhibit_number", trim(form.getExhibitNumber()));
        metadata.put("chain_of_custody", chainRows(form.getChainOfCustody()));
        return metadata;
    }

    private Map<String, Object> dispositionMetadata(
        UUID caseId,
        UploadDocumentForm form
    ) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("originating_disposition_id", existingDispositionId(caseId, form.getOriginatingDispositionId(), "originatingDispositionId"));
        metadata.put("sentence_terms", trim(form.getSentenceTerms()));
        metadata.put("prejudice_status", trim(form.getPrejudiceStatus()));
        return metadata;
    }

    private Map<String, Object> appealMetadata(
        UUID caseId,
        UploadDocumentForm form
    ) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("target_disposition_id", existingDispositionId(caseId, form.getTargetDispositionId(), "targetDispositionId"));
        metadata.put("grounds_for_appeal", trim(form.getGroundsForAppeal()));
        metadata.put("appellate_court_level", trim(form.getAppellateCourtLevel()));
        metadata.put("lower_court_record_verified", form.isLowerCourtRecordVerified());
        return metadata;
    }

    private List<Map<String, Object>> chainRows(List<ChainOfCustodyForm> forms) {
        if (forms == null) {
            return List.of();
        }
        return forms
            .stream()
            .filter(row ->
                hasText(row.getHandlerName()) ||
                hasText(row.getAction()) ||
                hasText(row.getTimestamp()) ||
                hasText(row.getLocation())
            )
            .map(row -> {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("handler_id", optionalUuid(row.getHandlerId(), "chainOfCustody"));
                item.put("handler_name", trim(row.getHandlerName()));
                item.put("action", trim(row.getAction()));
                item.put("timestamp", trim(row.getTimestamp()));
                item.put("location", trim(row.getLocation()));
                return item;
            })
            .toList();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> chainOfCustody(
        Map<String, Object> metadata
    ) {
        Object value = metadata.get("chain_of_custody");
        if (value instanceof List<?> list) {
            return list
                .stream()
                .filter(Map.class::isInstance)
                .map(item -> (Map<String, Object>) item)
                .toList();
        }
        return List.of();
    }

    private Map<String, Object> displayMetadata(Map<String, Object> metadata) {
        if (metadata.isEmpty()) {
            return metadata;
        }
        Map<String, Object> displayMetadata = new LinkedHashMap<>(metadata);
        Object ruledByJudgeId = displayMetadata.get("ruled_by_judge_id");
        if (ruledByJudgeId != null) {
            displayMetadata.put(
                "ruled_by_judge_id",
                findJudgeDisplayName(ruledByJudgeId)
            );
        }
        return displayMetadata;
    }

    private String findJudgeDisplayName(Object judgeId) {
        try {
            UUID id = judgeId instanceof UUID uuid
                ? uuid
                : UUID.fromString(judgeId.toString());
            return judgeRepository
                .findById(id)
                .map(this::formatJudgeName)
                .orElse(judgeId.toString());
        } catch (IllegalArgumentException ex) {
            return judgeId.toString();
        }
    }

    private UUID optionalUuid(String value, String fieldName) {
        if (!hasText(value)) {
            return null;
        }
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException ex) {
            throw new DocumentException(fieldName, "Selected value is invalid.");
        }
    }

    private String existingMotionId(UUID caseId, String documentId) {
        if (!hasText(documentId)) {
            return null;
        }
        Documents motion = mongoTemplate.findById(documentId, Documents.class);
        if (
            motion == null ||
            !caseId.equals(motion.getCaseId()) ||
            !"Motion".equals(motion.getDocumentType()) ||
            !isApprovedMotion(motion)
        ) {
            throw new DocumentException(
                "approvedMotionId",
                "Selected motion must be an approved motion for this case."
            );
        }
        return motion.getId();
    }

    private boolean isApprovedMotion(Documents motion) {
        Map<String, Object> metadata = motion.getMetadata() == null
            ? Map.of()
            : motion.getMetadata();
        return "Approved".equals(metadata.get("status"));
    }

    private UUID existingHearingId(
        UUID caseId,
        String value,
        String fieldName
    ) {
        UUID hearingId = optionalUuid(value, fieldName);
        if (hearingId == null) {
            return null;
        }
        boolean exists = hearingRepository
            .findById(hearingId)
            .filter(hearing -> caseId.equals(hearing.getCaseEntity().getCaseId()))
            .isPresent();
        if (!exists) {
            throw new DocumentException(
                fieldName,
                "Selected hearing does not exist for this case."
            );
        }
        return hearingId;
    }

    private UUID existingDispositionId(
        UUID caseId,
        String value,
        String fieldName
    ) {
        UUID dispositionId = optionalUuid(value, fieldName);
        if (dispositionId == null) {
            return null;
        }
        boolean exists = dispositionRepository
            .findById(dispositionId)
            .filter(disposition -> caseId.equals(disposition.getCaseEntity().getCaseId()))
            .isPresent();
        if (!exists) {
            throw new DocumentException(
                fieldName,
                "Selected disposition does not exist for this case."
            );
        }
        return dispositionId;
    }

    private String findUserDisplayName(UUID userId) {
        if (userId == null) {
            return "System";
        }
        return userRepository
            .findById(userId)
            .map(user -> user.getFirstName() + " " + user.getLastName())
            .orElse("Unknown user");
    }

    private String formatJudgeName(Judge judge) {
        return "Hon. " + judge.getFirstName() + " " + judge.getLastName();
    }

    private String formatDate(Instant instant) {
        return instant == null ? "" : DATE_FMT.format(instant);
    }

    private String formatDateTime(Instant instant) {
        return instant == null ? "" : DATE_TIME_FMT.format(instant);
    }

    private String prettify(String value) {
        return value == null ? "" : value;
    }

    private String badgeClass(String documentType) {
        return switch (documentType) {
            case "Filing" -> "badge--blue";
            case "Motion" -> "badge--indigo";
            case "Continuance" -> "badge--amber";
            case "Evidence" -> "badge--orange";
            case "Disposition" -> "badge--green";
            case "Appeal" -> "badge--purple";
            default -> "badge--slate";
        };
    }

    private String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
