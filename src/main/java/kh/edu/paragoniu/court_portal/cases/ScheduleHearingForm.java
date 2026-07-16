package kh.edu.paragoniu.court_portal.cases;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ScheduleHearingForm {

    @NotNull(message = "Please select a hearing type.")
    private Integer hearingTypeId;

    @NotNull(message = "Please select a courtroom.")
    private Integer courtroomId;

    @NotBlank(message = "Start date and time is required.")
    private String startAt;

    @NotBlank(message = "End date and time is required.")
    private String endAt;
}
