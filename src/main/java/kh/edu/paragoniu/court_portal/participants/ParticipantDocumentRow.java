package kh.edu.paragoniu.court_portal.participants;

import java.util.UUID;

/** View model for a row in the Participant Profile's Documents tab. */
public record ParticipantDocumentRow(
    String documentId,
    String title,
    String documentType,
    String badgeClass,
    UUID caseId,
    String caseNumber,
    String uploadedDate,
    boolean confidential
) implements java.io.Serializable {}
