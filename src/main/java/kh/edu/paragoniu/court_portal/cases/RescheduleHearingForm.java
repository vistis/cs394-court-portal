package kh.edu.paragoniu.court_portal.cases;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RescheduleHearingForm {

    @NotNull(message = "Courtroom is required.")
    private Integer courtroomId;

    @NotBlank(message = "New start date and time is required.")
    private String startAt;

    @NotBlank(message = "New end date and time is required.")
    private String endAt;
}
