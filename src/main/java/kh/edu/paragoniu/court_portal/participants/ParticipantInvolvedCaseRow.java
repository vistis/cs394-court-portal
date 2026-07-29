package kh.edu.paragoniu.court_portal.participants;

import java.util.UUID;

/** View model for a row in the Participant Profile's Involved Cases tab. */
public record ParticipantInvolvedCaseRow(
    UUID caseId,
    String caseNumber,
    String title,
    String classification,
    String status,
    String badgeClass,
    String role
) implements java.io.Serializable {}
