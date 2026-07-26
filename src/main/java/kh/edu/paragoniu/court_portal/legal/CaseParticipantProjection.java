package kh.edu.paragoniu.court_portal.legal;

import java.util.UUID;

/** Raw JPQL projection for a party (participant) within a case. */
public record CaseParticipantProjection(
    UUID participantId,
    String name,
    String partyType,
    String roleName
) {}
