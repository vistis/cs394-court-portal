package kh.edu.paragoniu.court_portal.participants;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.springframework.web.multipart.MultipartFile;

@Getter
@Setter
public class CreateParticipantForm {

    @NotBlank(message = "Party type is required.")
    private String partyType = "Individual";

    @NotBlank(message = "Name is required.")
    @Size(max = 255, message = "Name must be 255 characters or fewer.")
    private String name;

    @NotBlank(message = "Email address is required.")
    @Email(message = "Enter a valid email address.")
    private String email;

    @NotBlank(message = "Phone number is required.")
    private String phone;

    private MultipartFile profileImage;
}
