package kh.edu.paragoniu.court_portal.legal;

import java.io.Serializable;

/**
 * Display-ready row for the Lawyers tab of the Lawyers &amp; Judges directory.
 * Serializable so paginated results can be cached in Redis.
 */
public record LawyerRow(
    String lawyerId,
    String name,
    String barNumber,
    String firmOffice,
    long activeCases
) implements Serializable {}
