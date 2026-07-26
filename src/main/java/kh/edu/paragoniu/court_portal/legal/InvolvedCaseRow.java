package kh.edu.paragoniu.court_portal.legal;

import java.io.Serializable;

/**
 * Display-ready row in the "Involved Cases" list on the Lawyer Profile.
 * {@code representing} describes which party the lawyer represents on the case
 * (there is no free-text "counsel role" column in the fixed schema, so the party
 * they represent is the honest stand-in for the design's role label).
 * Serializable so it can be cached in Redis.
 */
public record InvolvedCaseRow(
    String caseId,
    String caseNumber,
    String title,
    String representing,
    String status,
    String badgeClass
) implements Serializable {}
