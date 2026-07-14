package kh.edu.paragoniu.court_portal.cases;

import java.util.UUID;

/** View model for a single row in the Cases Directory table. */
public record CaseRow(
    UUID caseId,
    String caseNumber,
    String title,
    String classification,
    String status,
    String badgeClass,
    String filedDate,
    String assignedJudge
) {}
