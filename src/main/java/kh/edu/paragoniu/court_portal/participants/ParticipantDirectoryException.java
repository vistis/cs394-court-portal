package kh.edu.paragoniu.court_portal.participants;

import lombok.Getter;

@Getter
public class ParticipantDirectoryException extends RuntimeException {

    private final String fieldName;

    public ParticipantDirectoryException(String fieldName, String message) {
        super(message);
        this.fieldName = fieldName;
    }
}
