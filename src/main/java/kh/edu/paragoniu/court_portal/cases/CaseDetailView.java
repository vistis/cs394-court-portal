package kh.edu.paragoniu.court_portal.cases;

import java.util.UUID;

public record CaseDetailView(
    UUID caseId,
    String caseNumber,
    String title,
    String description,
    String classification,
    String status,
    String badgeClass,
    String filedDate,
    String lastUpdatedDate,
    String closedDate,
    String visibility,
    AssignedJudgeView assignedJudge,
    AssignedGreffierView assignedGreffier
) implements java.io.Serializable {}
