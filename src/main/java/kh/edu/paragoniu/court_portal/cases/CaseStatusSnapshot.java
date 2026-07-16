package kh.edu.paragoniu.court_portal.cases;

import java.util.UUID;

public record CaseStatusSnapshot(
    UUID caseId,
    String caseNumber,
    String title,
    Integer statusId,
    String statusName
) {}
