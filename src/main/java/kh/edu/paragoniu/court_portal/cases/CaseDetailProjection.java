package kh.edu.paragoniu.court_portal.cases;

import java.time.Instant;
import java.util.UUID;

public record CaseDetailProjection(
    UUID caseId,
    String caseNumber,
    String title,
    String description,
    String classificationName,
    String statusName,
    Instant filedAt,
    Instant lastUpdatedAt,
    Instant closedAt,
    boolean publicCase
) {}
