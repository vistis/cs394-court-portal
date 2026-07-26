package kh.edu.paragoniu.court_portal.legal;

import java.util.UUID;

/** Raw JPQL projection for the Judge Profile header/information cards. */
public record JudgeProfileProjection(
    UUID judgeId,
    String firstName,
    String lastName,
    String licenseNumber,
    String profilePicturePath
) {}
