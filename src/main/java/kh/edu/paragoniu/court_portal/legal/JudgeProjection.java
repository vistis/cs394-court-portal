package kh.edu.paragoniu.court_portal.legal;

import java.util.UUID;

/** Raw JPQL projection for a judge row (with a correlated active-case count). */
public record JudgeProjection(
    UUID judgeId,
    String firstName,
    String lastName,
    boolean active,
    Long activeCases
) {}
