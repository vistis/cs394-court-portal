package kh.edu.paragoniu.court_portal.legal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.springframework.web.multipart.MultipartFile;

/** Form backing the Register New Lawyer page. */
@Getter
@Setter
public class CreateLawyerForm {

    @NotBlank(message = "First name is required.")
    @Size(max = 100, message = "First name must be 100 characters or fewer.")
    private String firstName;

    @NotBlank(message = "Last name is required.")
    @Size(max = 100, message = "Last name must be 100 characters or fewer.")
    private String lastName;

    @NotBlank(message = "Bar number is required.")
    @Size(max = 100, message = "Bar number must be 100 characters or fewer.")
    private String barNumber;

    @Size(max = 255, message = "Firm name must be 255 characters or fewer.")
    private String firmName;

    private MultipartFile profileImage;
}
