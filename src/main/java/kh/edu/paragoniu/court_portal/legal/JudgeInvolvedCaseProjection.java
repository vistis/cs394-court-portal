package kh.edu.paragoniu.court_portal.legal;

import java.util.UUID;

/** Raw JPQL projection for one case a judge is assigned to (via case_judges). */
public record JudgeInvolvedCaseProjection(
    UUID caseId,
    String caseNumber,
    String title,
    boolean presiding,
    String statusName
) {}
