package kh.edu.paragoniu.court_portal.cases;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class CreateDocketEntryForm {

    @NotBlank(message = "Please select an activity type.")
    private String activityType;

    @NotBlank(message = "Description is required.")
    @Size(max = 2000, message = "Description must be 2000 characters or fewer.")
    private String description;

    public String getActivityType() {
        return activityType;
    }

    public void setActivityType(String activityType) {
        this.activityType = activityType;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
