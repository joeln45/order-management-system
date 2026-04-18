package com.joel.ordermanagement.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Body for {@code POST /auth/register}. All fields are validated by
 * Hibernate Validator before the controller method is entered; failures
 * are aggregated into a single RFC 7807 ProblemDetail by
 * {@link com.joel.ordermanagement.exception.GlobalExceptionHandler}.
 */
@Data
public class RegisterRequest {

    @NotBlank(message = "Username is required")
    @Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
    @Pattern(regexp = "^[a-zA-Z0-9_.-]+$",
            message = "Username may contain letters, digits, underscore, dot and hyphen only")
    private String username;

    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 100, message = "Password must be at least 8 characters")
    private String password;

    @NotBlank(message = "Name is required")
    @Size(max = 100)
    private String name;

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be a valid email address")
    private String email;

    @NotBlank(message = "Postal address is required")
    @Size(max = 255)
    private String postalAddress;
}
