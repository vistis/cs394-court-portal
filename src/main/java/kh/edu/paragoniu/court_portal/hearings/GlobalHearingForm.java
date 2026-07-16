package kh.edu.paragoniu.court_portal.hearings;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/** Backing object for the global Schedule Hearing form. */
@Getter
@Setter
public class GlobalHearingForm {

    @NotBlank(message = "Please select a case.")
    private String caseId;

    /** Display text for the chosen case (case number / title) — not persisted. */
    private String caseReference;

    @NotNull(message = "Please select a hearing type.")
    private Integer hearingTypeId;

    @NotNull(message = "Please select a courtroom.")
    private Integer courtroomId;

    @NotBlank(message = "Start date and time is required.")
    private String startAt;

    @NotBlank(message = "End date and time is required.")
    private String endAt;
}
