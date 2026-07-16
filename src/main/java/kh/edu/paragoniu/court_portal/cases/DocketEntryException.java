package kh.edu.paragoniu.court_portal.cases;

public class DocketEntryException extends RuntimeException {

    private final String fieldName;

    public DocketEntryException(String fieldName, String message) {
        super(message);
        this.fieldName = fieldName;
    }

    public String getFieldName() {
        return fieldName;
    }
}
