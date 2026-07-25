package kh.edu.paragoniu.court_portal.legal;

import java.util.UUID;

/** Raw JPQL projection for a lawyer row (with a correlated active-case count). */
public record LawyerProjection(
    UUID lawyerId,
    String firstName,
    String lastName,
    String licenseNumber,
    String firmName,
    Long activeCases
) {}
