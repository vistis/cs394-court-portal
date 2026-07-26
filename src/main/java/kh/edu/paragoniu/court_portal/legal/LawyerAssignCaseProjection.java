package kh.edu.paragoniu.court_portal.legal;

import java.util.UUID;

/** Raw JPQL projection for a case suggestion in the Assign Case to Lawyer search. */
public record LawyerAssignCaseProjection(
    UUID caseId,
    String caseNumber,
    String title,
    String statusName
) {}
