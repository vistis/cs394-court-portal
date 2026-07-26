package kh.edu.paragoniu.court_portal.legal;

/**
 * A single case suggestion returned to the "Assign Case to Judge" modal
 * typeahead (JSON). {@code alreadyAssigned} lets the UI disable cases this
 * judge is already on.
 */
public record JudgeAssignCaseOption(
    String caseId,
    String caseNumber,
    String title,
    String status,
    String badgeClass,
    boolean alreadyAssigned
) {}
