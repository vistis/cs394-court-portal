package kh.edu.paragoniu.court_portal.cases;

import jakarta.validation.constraints.NotNull;

public class UpdateCaseStatusForm {

    @NotNull(message = "Please select a new status.")
    private Integer statusId;

    public Integer getStatusId() {
        return statusId;
    }

    public void setStatusId(Integer statusId) {
        this.statusId = statusId;
    }
}
