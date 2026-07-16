package kh.edu.paragoniu.court_portal.cases;

import java.util.UUID;

public record HearingRescheduleView(
    UUID hearingId,
    String hearingType,
    String currentSchedule,
    Integer courtroomId
) {}
