package kh.edu.paragoniu.court_portal.legal;

import java.util.UUID;

/** Raw JPQL projection for the Lawyer Profile header/information cards. */
public record LawyerProfileProjection(
    UUID lawyerId,
    String firstName,
    String lastName,
    String licenseNumber,
    String firmName,
    String profilePicturePath
) {}
