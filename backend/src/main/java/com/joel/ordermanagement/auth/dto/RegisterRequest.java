package com.joel.ordermanagement.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "New customer registration payload")
public class RegisterRequest {

    @NotBlank(message = "Username is required")
    @Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
    @Pattern(regexp = "^[a-zA-Z0-9_.-]+$",
            message = "Username may contain letters, digits, underscore, dot and hyphen only")
    @Schema(description = "Desired username (letters, digits, _ . -)", example = "alice42", minLength = 3, maxLength = 50)
    private String username;

    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 100, message = "Password must be at least 8 characters")
    @Schema(description = "Password (min 8 chars)", example = "correct-horse-battery-staple", minLength = 8)
    private String password;

    @NotBlank(message = "Name is required")
    @Size(max = 100)
    @Schema(description = "Full name", example = "Alice Example")
    private String name;

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be a valid email address")
    @Schema(description = "Email address", example = "alice@example.com")
    private String email;

    @NotBlank(message = "Postal address is required")
    @Size(max = 255)
    @Schema(description = "Shipping address", example = "10 Downing Street, London, SW1A 2AA")
    private String postalAddress;
}
