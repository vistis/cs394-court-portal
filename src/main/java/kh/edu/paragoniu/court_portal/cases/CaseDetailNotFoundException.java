package kh.edu.paragoniu.court_portal.cases;

import java.util.UUID;

public class CaseDetailNotFoundException extends RuntimeException {

    public CaseDetailNotFoundException(UUID caseId) {
        super("Case was not found: " + caseId);
    }
}
