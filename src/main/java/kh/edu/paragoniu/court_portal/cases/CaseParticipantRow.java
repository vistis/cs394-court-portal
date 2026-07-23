package kh.edu.paragoniu.court_portal.cases;

import java.util.UUID;

public record CaseParticipantRow(
    UUID participantId,
    String name,
    String partyType,
    String role,
    String contactInfo
) implements java.io.Serializable {}
