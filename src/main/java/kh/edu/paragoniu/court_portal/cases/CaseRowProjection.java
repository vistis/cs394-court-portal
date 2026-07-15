package kh.edu.paragoniu.court_portal.cases;

import java.time.Instant;
import java.util.UUID;

public record CaseRowProjection(
    UUID caseId,
    String caseNumber,
    String title,
    String classificationName,
    String statusName,
    Instant filedAt,
    String judgeFirstName,
    String judgeLastName
) {}
