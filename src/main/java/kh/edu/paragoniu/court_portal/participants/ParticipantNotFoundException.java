package kh.edu.paragoniu.court_portal.participants;

import java.util.UUID;

public class ParticipantNotFoundException extends RuntimeException {

    public ParticipantNotFoundException(UUID participantId) {
        super("Participant was not found: " + participantId);
    }
}
