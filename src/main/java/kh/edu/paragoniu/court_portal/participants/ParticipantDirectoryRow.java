package kh.edu.paragoniu.court_portal.participants;

import java.util.UUID;

/** View model for a single row in the global Participants Directory table. */
public record ParticipantDirectoryRow(
    UUID participantId,
    String name,
    String partyType,
    String badgeClass,
    String primaryEmail,
    long involvedCases
) implements java.io.Serializable {}
