package kh.edu.paragoniu.court_portal.greffier;

import java.io.Serializable;

/**
 * Display-ready row for the greffier "Assigned Cases" table.
 * Serializable so paginated results can be cached in Redis.
 */
public record AssignedCaseRow(
    String caseId,
    String caseNumber,
    String title,
    String classification,
    String status,
    String badgeClass,
    String assignedBy,
    String assignedAt
) implements Serializable {}
