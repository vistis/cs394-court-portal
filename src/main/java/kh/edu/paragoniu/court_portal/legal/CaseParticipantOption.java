package kh.edu.paragoniu.court_portal.legal;

/**
 * A party (participant) within a selected case, offered as the "who does this
 * lawyer represent?" choice in the Assign Case to Lawyer modal (JSON).
 * {@code alreadyAssigned} flags parties this lawyer already represents on the
 * case so the UI can disable them.
 */
public record CaseParticipantOption(
    String participantId,
    String name,
    String partyType,
    String role,
    boolean alreadyAssigned
) {}
