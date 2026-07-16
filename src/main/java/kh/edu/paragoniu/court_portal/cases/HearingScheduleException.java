package kh.edu.paragoniu.court_portal.cases;

public class HearingScheduleException extends RuntimeException {

    private final String fieldName;

    public HearingScheduleException(String fieldName, String message) {
        super(message);
        this.fieldName = fieldName;
    }

    public String getFieldName() {
        return fieldName;
    }
}
