package kh.edu.paragoniu.court_portal.hearings;

import java.time.Instant;
import java.util.UUID;

/** Raw JPQL projection for a hearing row, before display formatting. */
public record HearingProjection(
    UUID hearingId,
    UUID caseId,
    String caseNumber,
    String hearingType,
    String courtroom,
    Instant startAt,
    Instant endAt,
    String status
) {}
