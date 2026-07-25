package kh.edu.paragoniu.court_portal.greffier;

import java.util.UUID;

/** Raw JPQL projection for a case suggestion in the Assign Case search. */
public record AssignCaseProjection(
    UUID caseId,
    String caseNumber,
    String title,
    String statusName
) {}
