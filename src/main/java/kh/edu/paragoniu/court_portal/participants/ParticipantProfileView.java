package kh.edu.paragoniu.court_portal.participants;

import java.util.UUID;

/** View model for the Participant Profile page's header card and details panel. */
public record ParticipantProfileView(
    UUID participantId,
    String name,
    String initials,
    String partyType,
    String badgeClass,
    String nameLabel,
    String primaryEmail,
    String primaryPhone,
    String profileImageUrl
) implements java.io.Serializable {}
