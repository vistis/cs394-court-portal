package kh.edu.paragoniu.court_portal.cases;

public class StatusUpdateException extends RuntimeException {

    private final String fieldName;

    public StatusUpdateException(String fieldName, String message) {
        super(message);
        this.fieldName = fieldName;
    }

    public String getFieldName() {
        return fieldName;
    }
}
