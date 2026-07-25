package kh.edu.paragoniu.court_portal.greffier;

import java.util.UUID;

/**
 * Raw JPQL projection for a greffier row on the Greffier Management screen.
 * {@code assignedCases} is a correlated COUNT over case_assignments and is
 * boxed ({@link Long}) because JPQL COUNT yields {@code Long}.
 */
public record GreffierProjection(
    UUID userId,
    String firstName,
    String lastName,
    String email,
    String roleName,
    Long assignedCases
) {}
