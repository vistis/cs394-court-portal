package kh.edu.paragoniu.court_portal.greffier;

/**
 * A single case suggestion returned to the Assign Case modal typeahead (JSON).
 * {@code alreadyAssigned} lets the UI disable cases this greffier already holds.
 */
public record AssignCaseOption(
    String caseId,
    String caseNumber,
    String title,
    String status,
    String badgeClass,
    boolean alreadyAssigned
) {}
