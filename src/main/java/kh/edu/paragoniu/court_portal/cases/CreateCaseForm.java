package kh.edu.paragoniu.court_portal.cases;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;

public class CreateCaseForm {

    @NotBlank(message = "Case title is required.")
    @Size(max = 255, message = "Case title must be 255 characters or fewer.")
    private String title;

    @NotBlank(message = "Description is required.")
    @Size(max = 5000, message = "Description must be 5000 characters or fewer.")
    private String description;

    @NotNull(message = "Filed date is required.")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate filedDate = LocalDate.now();

    @NotNull(message = "Classification is required.")
    private Integer classificationId;

    private UUID judgeId;

    private boolean publicCase;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public LocalDate getFiledDate() {
        return filedDate;
    }

    public void setFiledDate(LocalDate filedDate) {
        this.filedDate = filedDate;
    }

    public Integer getClassificationId() {
        return classificationId;
    }

    public void setClassificationId(Integer classificationId) {
        this.classificationId = classificationId;
    }

    public UUID getJudgeId() {
        return judgeId;
    }

    public void setJudgeId(UUID judgeId) {
        this.judgeId = judgeId;
    }

    public boolean isPublicCase() {
        return publicCase;
    }

    public void setPublicCase(boolean publicCase) {
        this.publicCase = publicCase;
    }
}
