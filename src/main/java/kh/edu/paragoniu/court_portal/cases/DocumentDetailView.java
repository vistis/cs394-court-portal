package kh.edu.paragoniu.court_portal.cases;

import java.util.List;
import java.util.Map;

public record DocumentDetailView(
    String documentId,
    String caseId,
    String caseNumber,
    String title,
    String documentType,
    String documentTypeLabel,
    String badgeClass,
    String submittedBy,
    String uploadedAt,
    boolean confidential,
    String fileUrl,
    String filePath,
    Map<String, Object> metadata,
    List<Map<String, Object>> chainOfCustody
) implements java.io.Serializable {}
