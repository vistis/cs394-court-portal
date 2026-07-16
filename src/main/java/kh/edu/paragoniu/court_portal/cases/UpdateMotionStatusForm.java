package kh.edu.paragoniu.court_portal.cases;

import jakarta.validation.constraints.NotBlank;

public class UpdateMotionStatusForm {

    @NotBlank(message = "Motion status is required.")
    private String status;

    private String ruledByJudgeId;
    private String ruledAt;

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getRuledByJudgeId() {
        return ruledByJudgeId;
    }

    public void setRuledByJudgeId(String ruledByJudgeId) {
        this.ruledByJudgeId = ruledByJudgeId;
    }

    public String getRuledAt() {
        return ruledAt;
    }

    public void setRuledAt(String ruledAt) {
        this.ruledAt = ruledAt;
    }
}
