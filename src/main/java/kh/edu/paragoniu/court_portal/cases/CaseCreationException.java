package kh.edu.paragoniu.court_portal.cases;

public class CaseCreationException extends RuntimeException {

    private final String fieldName;

    public CaseCreationException(String fieldName, String message) {
        super(message);
        this.fieldName = fieldName;
    }

    public String getFieldName() {
        return fieldName;
    }
}
