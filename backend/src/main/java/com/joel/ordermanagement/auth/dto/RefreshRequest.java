package com.joel.ordermanagement.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "Refresh-token payload for POST /auth/refresh and /auth/logout")
public class RefreshRequest {

    @NotBlank(message = "refreshToken is required")
    @Schema(description = "The opaque refresh token returned by /auth/login",
            example = "xR3kP9qL...base64url-32-bytes")
    private String refreshToken;
}
