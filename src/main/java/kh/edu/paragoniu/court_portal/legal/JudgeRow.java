package kh.edu.paragoniu.court_portal.legal;

import java.io.Serializable;

/**
 * Display-ready row for the Judges tab of the Lawyers &amp; Judges directory.
 * {@code chambers} has no backing column in the fixed schema (judges aren't
 * tied to a courtroom), so it is always "N/A". Serializable so paginated
 * results can be cached in Redis.
 */
public record JudgeRow(
    String judgeId,
    String name,
    String chambers,
    String status,
    String statusBadgeClass,
    long activeCases
) implements Serializable {}
