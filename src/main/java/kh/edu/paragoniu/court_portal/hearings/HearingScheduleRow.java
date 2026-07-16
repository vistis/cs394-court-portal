package kh.edu.paragoniu.court_portal.hearings;

/** A single row on the global Hearings Schedule table (display-ready). */
public record HearingScheduleRow(
    String caseId,
    String caseNumber,
    String hearingType,
    String courtroom,
    String startTime,
    String endTime,
    String status,
    String badgeClass
) {}
