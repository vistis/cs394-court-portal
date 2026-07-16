package kh.edu.paragoniu.court_portal.cases;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;

public class CreateDispositionForm {

    @NotNull(message = "Please select an outcome.")
    private Integer outcomeTypeId;

    @NotNull(message = "Disposition date is required.")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate dispositionDate = LocalDate.now();

    @NotBlank(message = "Ruling summary is required.")
    private String rulingSummary;

    public Integer getOutcomeTypeId() {
        return outcomeTypeId;
    }

    public void setOutcomeTypeId(Integer outcomeTypeId) {
        this.outcomeTypeId = outcomeTypeId;
    }

    public LocalDate getDispositionDate() {
        return dispositionDate;
    }

    public void setDispositionDate(LocalDate dispositionDate) {
        this.dispositionDate = dispositionDate;
    }

    public String getRulingSummary() {
        return rulingSummary;
    }

    public void setRulingSummary(String rulingSummary) {
        this.rulingSummary = rulingSummary;
    }
}
