package kh.edu.paragoniu.court_portal.cases;

import java.time.Instant;

public record AssignedGreffierProjection(
    String firstName,
    String lastName,
    String roleName,
    Instant assignedAt
) {}
