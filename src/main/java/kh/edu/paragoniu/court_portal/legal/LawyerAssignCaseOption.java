package kh.edu.paragoniu.court_portal.legal;

/**
 * A single case suggestion returned to the "Assign Case to Lawyer" modal
 * typeahead (JSON). Unlike judges, a lawyer represents a specific party, so
 * picking a case is only the first step — the party is chosen next.
 */
public record LawyerAssignCaseOption(
    String caseId,
    String caseNumber,
    String title,
    String status,
    String badgeClass
) {}
