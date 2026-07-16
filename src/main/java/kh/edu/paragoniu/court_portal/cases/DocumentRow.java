package kh.edu.paragoniu.court_portal.cases;

public record DocumentRow(
    String documentId,
    String type,
    String typeLabel,
    String badgeClass,
    String title,
    String uploadedDate,
    boolean confidential
) {}
