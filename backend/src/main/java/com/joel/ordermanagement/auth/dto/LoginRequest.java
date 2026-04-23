package com.joel.ordermanagement.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "Credentials payload for POST /auth/login")
public class LoginRequest {

    @NotBlank(message = "Username is required")
    @Schema(description = "Account username", example = "operator")
    private String username;

    @NotBlank(message = "Password is required")
    @Schema(description = "Account password (plaintext over TLS)", example = "password123")
    private String password;
}
