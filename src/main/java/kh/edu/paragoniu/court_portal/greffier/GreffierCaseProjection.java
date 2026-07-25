package kh.edu.paragoniu.court_portal.greffier;

import java.time.Instant;
import java.util.UUID;

/**
 * Raw JPQL projection for one assigned-case row on a greffier's detail page.
 */
public record GreffierCaseProjection(
    UUID caseId,
    String caseNumber,
    String title,
    String classificationName,
    String statusName,
    String assignedByFirstName,
    String assignedByLastName,
    Instant assignedAt
) {}
