package kh.edu.paragoniu.court_portal.cases;

public class DispositionException extends RuntimeException {

    private final String fieldName;

    public DispositionException(String fieldName, String message) {
        super(message);
        this.fieldName = fieldName;
    }

    public String getFieldName() {
        return fieldName;
    }
}
