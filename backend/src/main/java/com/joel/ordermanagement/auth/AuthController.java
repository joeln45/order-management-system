package com.joel.ordermanagement.auth;

import com.joel.ordermanagement.auth.dto.AuthResponse;
import com.joel.ordermanagement.auth.dto.LoginRequest;
import com.joel.ordermanagement.auth.dto.RefreshRequest;
import com.joel.ordermanagement.auth.dto.RegisterRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public authentication endpoints. All routes are allow-listed in
 * {@link com.joel.ordermanagement.config.SecurityConfig}.
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@CrossOrigin
@Tag(name = "Authentication", description = "Register, log in, rotate refresh tokens, and log out")
@SecurityRequirements({})  // entire controller is public; override the global bearer requirement
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    @Operation(
            summary = "Register a new customer account",
            description = """
                    Creates a `User` with `ROLE_CUSTOMER` and a linked `Customer` profile
                    in a single transaction. Returns access and refresh tokens so the
                    caller is logged in immediately.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Account created; tokens returned"),
            @ApiResponse(responseCode = "400", description = "Validation error",
                    content = @Content(schema = @Schema(implementation = Void.class))),
            @ApiResponse(responseCode = "409", description = "Username or email already in use",
                    content = @Content(schema = @Schema(implementation = Void.class))),
            @ApiResponse(responseCode = "429", description = "Rate limit exceeded (10/min/IP)",
                    content = @Content(schema = @Schema(implementation = Void.class)))
    })
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @PostMapping("/login")
    @Operation(
            summary = "Log in with username + password",
            description = "Returns a short-lived access token (15 min) and a rotating refresh token (7 d).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Credentials accepted; tokens returned"),
            @ApiResponse(responseCode = "401", description = "Invalid username or password",
                    content = @Content(schema = @Schema(implementation = Void.class))),
            @ApiResponse(responseCode = "429", description = "Rate limit exceeded (10/min/IP)",
                    content = @Content(schema = @Schema(implementation = Void.class)))
    })
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/refresh")
    @Operation(
            summary = "Rotate the refresh token",
            description = """
                    Exchanges a valid refresh token for a new access + refresh token pair.
                    The old refresh token is revoked (one-time-use); detect token theft by
                    watching for refresh attempts with an already-rotated token.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "New token pair issued"),
            @ApiResponse(responseCode = "401", description = "Refresh token is expired, revoked or unknown",
                    content = @Content(schema = @Schema(implementation = Void.class)))
    })
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return ResponseEntity.ok(authService.refresh(request.getRefreshToken()));
    }

    @PostMapping("/logout")
    @Operation(
            summary = "Log out (revoke a refresh token)",
            description = "Deletes the supplied refresh token from the server. Access tokens remain valid until they expire naturally.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Refresh token revoked (idempotent)")
    })
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshRequest request) {
        authService.logout(request.getRefreshToken());
        return ResponseEntity.noContent().build();
    }
}
