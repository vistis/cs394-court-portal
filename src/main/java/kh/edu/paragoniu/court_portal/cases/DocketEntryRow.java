package kh.edu.paragoniu.court_portal.cases;

public record DocketEntryRow(
    String date,
    String activityType,
    String badgeClass,
    String description,
    String filedBy
) {}
