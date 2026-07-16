package kh.edu.paragoniu.court_portal.hearings;

import java.util.UUID;

/** A single case suggestion returned to the Case Reference typeahead. */
public record CaseLookupResult(UUID caseId, String caseNumber, String title) {}
