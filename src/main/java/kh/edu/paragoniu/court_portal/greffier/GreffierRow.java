package kh.edu.paragoniu.court_portal.greffier;

import java.io.Serializable;

/**
 * Display-ready greffier row for the Greffier Management table.
 * Serializable so paginated results can be cached in Redis.
 */
public record GreffierRow(
    String userId,
    String fullName,
    String initials,
    String email,
    String role,
    long assignedCases
) implements Serializable {}
