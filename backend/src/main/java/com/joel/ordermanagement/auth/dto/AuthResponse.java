package com.joel.ordermanagement.auth.dto;

import com.joel.ordermanagement.auth.Role;
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
public class AuthResponse {
    private String accessToken;
    private String refreshToken;
    private long accessTokenExpiresInSeconds;
    private String username;
    private Role role;
}
