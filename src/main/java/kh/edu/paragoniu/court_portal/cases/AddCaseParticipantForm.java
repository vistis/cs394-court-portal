package kh.edu.paragoniu.court_portal.cases;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AddCaseParticipantForm {

    @NotBlank(message = "Party type is required.")
    private String partyType = "Individual";

    @NotNull(message = "Participant is required.")
    private UUID participantId;

    @NotNull(message = "Role is required.")
    private Integer roleId;
}
