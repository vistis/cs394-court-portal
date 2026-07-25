package kh.edu.paragoniu.court_portal.hearings;

import java.io.Serializable;

/** Display-ready detail of a single hearing for the Hearing Details page. */
public record HearingDetailView(
    String hearingId,
    String caseId,
    String caseNumber,
    String caseTitle,
    String hearingType,
    String courtroom,
    Integer courtroomId,
    String status,
    String badgeClass,
    String startTime,
    String endTime
) implements Serializable {}
