package kh.edu.paragoniu.court_portal.legal;

import lombok.Getter;

/** Field-scoped validation/creation failure for the Lawyers &amp; Judges module. */
@Getter
public class LawyerJudgeException extends RuntimeException {

    private final String fieldName;

    public LawyerJudgeException(String fieldName, String message) {
        super(message);
        this.fieldName = fieldName;
    }
}
