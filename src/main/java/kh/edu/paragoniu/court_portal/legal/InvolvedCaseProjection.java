package kh.edu.paragoniu.court_portal.legal;

import java.util.UUID;

/** Raw JPQL projection for one case a lawyer is involved in (via a party). */
public record InvolvedCaseProjection(
    UUID caseId,
    String caseNumber,
    String title,
    String participantName,
    String partyType,
    String statusName
) {}
