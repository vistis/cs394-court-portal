package kh.edu.paragoniu.court_portal.hearings;

import java.io.Serializable;

/** A single row on the global Hearings Schedule table (display-ready). */
public record HearingScheduleRow(
    String hearingId,
    String caseId,
    String caseNumber,
    String hearingType,
    String courtroom,
    String startTime,
    String endTime,
    String status,
    String badgeClass
) implements Serializable {}
