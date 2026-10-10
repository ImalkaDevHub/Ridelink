package com.ridelink.account_service.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

// Boundary tests for the bean-validation rules on the request DTOs.
class RequestValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    private RegisterRequest register(String name, String email, String password) {
        RegisterRequest request = new RegisterRequest();
        request.setName(name);
        request.setEmail(email);
        request.setPassword(password);
        return request;
    }

    private Set<String> invalidFields(Object request) {
        Set<ConstraintViolation<Object>> violations = validator.validate(request);
        return violations.stream().map(v -> v.getPropertyPath().toString()).collect(Collectors.toSet());
    }

    @Test
    void validRegistrationHasNoViolations() {
        assertThat(invalidFields(register("Nimal", "nimal@example.com", "secret123"))).isEmpty();
    }

    @Test
    void nameLengthBoundaries() {
        assertThat(invalidFields(register("N", "n@example.com", "secret123"))).containsExactly("name");
        assertThat(invalidFields(register("Ni", "n@example.com", "secret123"))).isEmpty();
        assertThat(invalidFields(register("x".repeat(60), "n@example.com", "secret123"))).isEmpty();
        assertThat(invalidFields(register("x".repeat(61), "n@example.com", "secret123"))).containsExactly("name");
    }

    @Test
    void invalidEmailIsRejected() {
        assertThat(invalidFields(register("Nimal", "not-an-email", "secret123"))).containsExactly("email");
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc1234", "abcdefgh", "12345678", ""})
    void weakPasswordsAreRejected(String password) {
        assertThat(invalidFields(register("Nimal", "n@example.com", password))).containsExactly("password");
    }

    @Test
    void passwordLengthBoundaries() {
        assertThat(invalidFields(register("Nimal", "n@example.com", "abcdefg1"))).isEmpty();          // 8
        assertThat(invalidFields(register("Nimal", "n@example.com", "a1" + "x".repeat(70)))).isEmpty(); // 72
        assertThat(invalidFields(register("Nimal", "n@example.com", "a1" + "x".repeat(71))))         // 73
                .containsExactly("password");
    }

    @Test
    void passwordChangeAppliesTheSameRuleToTheNewPassword() {
        PasswordChangeRequest request = new PasswordChangeRequest();
        request.setCurrentPassword("anything");
        request.setNewPassword("short1");

        assertThat(invalidFields(request)).containsExactly("newPassword");
    }

    @Test
    void loginDoesNotApplyThePasswordRule() {
        // Accounts created before the 8-char rule must still be able to log in.
        LoginRequest request = new LoginRequest();
        request.setEmail("n@example.com");
        request.setPassword("abc123");

        assertThat(invalidFields(request)).isEmpty();
    }
}
