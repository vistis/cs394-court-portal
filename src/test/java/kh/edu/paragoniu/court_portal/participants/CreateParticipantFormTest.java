package kh.edu.paragoniu.court_portal.participants;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CreateParticipantFormTest {

    private final Validator validator = Validation
        .buildDefaultValidatorFactory()
        .getValidator();

    @Test
    void validFormHasNoViolations() {
        assertThat(validator.validate(validForm())).isEmpty();
    }

    @Test
    void missingNameFailsValidation() {
        CreateParticipantForm form = validForm();
        form.setName(" ");
        assertThat(propertyPaths(form)).contains("name");
    }

    @Test
    void nameOver255CharactersFailsValidation() {
        CreateParticipantForm form = validForm();
        form.setName("A".repeat(256));
        assertThat(propertyPaths(form)).contains("name");
    }

    @Test
    void missingEmailFailsValidation() {
        CreateParticipantForm form = validForm();
        form.setEmail(" ");
        assertThat(propertyPaths(form)).contains("email");
    }

    @Test
    void malformedEmailFailsValidation() {
        CreateParticipantForm form = validForm();
        form.setEmail("not-an-email");
        assertThat(propertyPaths(form)).contains("email");
    }

    @Test
    void missingPhoneFailsValidation() {
        CreateParticipantForm form = validForm();
        form.setPhone(" ");
        assertThat(propertyPaths(form)).contains("phone");
    }

    @Test
    void missingPartyTypeFailsValidation() {
        CreateParticipantForm form = validForm();
        form.setPartyType(" ");
        assertThat(propertyPaths(form)).contains("partyType");
    }

    private Set<String> propertyPaths(CreateParticipantForm form) {
        Set<ConstraintViolation<CreateParticipantForm>> violations = validator.validate(
            form
        );
        return violations
            .stream()
            .map(v -> v.getPropertyPath().toString())
            .collect(java.util.stream.Collectors.toSet());
    }

    private CreateParticipantForm validForm() {
        CreateParticipantForm form = new CreateParticipantForm();
        form.setPartyType("Individual");
        form.setName("Michael Henderson");
        form.setEmail("m.henderson@email.com");
        form.setPhone("555-0192");
        return form;
    }
}
