package com.joel.ordermanagement.auth.dto;

import com.joel.ordermanagement.auth.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response for {@code /auth/login}, {@code /auth/register}, and
 * {@code /auth/refresh}. The client keeps {@code accessToken} in memory
 * (short-lived) and the {@code refreshToken} in a secure store.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Token pair + account info returned by the /auth endpoints")
public class AuthResponse {

    @Schema(description = "Short-lived JWT (15 min). Send as `Authorization: Bearer <token>`.",
            example = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIi...")
    private String accessToken;

    @Schema(description = "Opaque refresh token (7 days). Single-use, rotated on every /auth/refresh.",
            example = "xR3kP9qL...base64url-32-bytes")
    private String refreshToken;

    @Schema(description = "Seconds until the access token expires", example = "900")
    private long accessTokenExpiresInSeconds;

    @Schema(example = "operator")
    private String username;

    @Schema(description = "Role assigned to the user", example = "OPERATOR")
    private Role role;

    @Schema(description = "Business customer id linked to this account; null for operators",
            example = "CUST001", nullable = true)
    private String customerId;

    @Schema(description = "Display name of the linked customer profile; null for operators",
            example = "Demo Customer", nullable = true)
    private String customerName;
}
