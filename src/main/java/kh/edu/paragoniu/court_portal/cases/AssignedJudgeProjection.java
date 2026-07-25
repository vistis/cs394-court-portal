package kh.edu.paragoniu.court_portal.cases;

public record AssignedJudgeProjection(
    String firstName,
    String lastName,
    String licenseNumber
) implements java.io.Serializable {}
