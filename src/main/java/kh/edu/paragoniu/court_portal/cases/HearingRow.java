package kh.edu.paragoniu.court_portal.cases;

public record HearingRow(
    String hearingId,
    String month,
    String day,
    String hearingType,
    String timeRange,
    String courtroom,
    String judgeName,
    String status,
    String badgeClass
) {}
