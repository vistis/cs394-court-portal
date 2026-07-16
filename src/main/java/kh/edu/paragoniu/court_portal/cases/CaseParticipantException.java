package kh.edu.paragoniu.court_portal.cases;

import lombok.Getter;

@Getter
public class CaseParticipantException extends RuntimeException {

    private final String fieldName;

    public CaseParticipantException(String fieldName, String message) {
        super(message);
        this.fieldName = fieldName;
    }
}
