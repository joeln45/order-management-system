package com.joel.ordermanagement.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.joel.ordermanagement.auth.dto.AuthResponse;
import com.joel.ordermanagement.auth.dto.LoginRequest;
import com.joel.ordermanagement.auth.dto.RefreshRequest;
import com.joel.ordermanagement.auth.dto.RegisterRequest;
import com.joel.ordermanagement.exception.BusinessRuleException;
import com.joel.ordermanagement.exception.UnauthorizedException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice tests for {@link AuthController}. Boots only the MVC stack + Jackson +
 * Bean Validation + {@code @RestControllerAdvice}, with the service layer
 * mocked. Security filter chain is disabled here; real 401/403 behaviour is
 * covered by the integration test.
 */
@WebMvcTest(controllers = AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class AuthControllerTest {

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;

    @MockBean private AuthService authService;
    @MockBean private JwtService jwtService;   // satisfies JwtAuthFilter's constructor dep in the slice

    // =============================================================
    // POST /auth/register
    // =============================================================

    @Test
    void register_validBody_returns201WithTokens() throws Exception {
        RegisterRequest body = new RegisterRequest();
        body.setUsername("alice");
        body.setPassword("s3cret-password");
        body.setName("Alice Example");
        body.setEmail("alice@example.com");
        body.setPostalAddress("10 Downing St");

        when(authService.register(any(RegisterRequest.class)))
                .thenReturn(new AuthResponse("jwt.access", "raw-refresh", 900, "alice", Role.CUSTOMER, "CUST001", "Alice Example"));

        mvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").value("jwt.access"))
                .andExpect(jsonPath("$.refreshToken").value("raw-refresh"))
                .andExpect(jsonPath("$.username").value("alice"))
                .andExpect(jsonPath("$.role").value("CUSTOMER"));
    }

    @Test
    void register_shortPassword_returns400ProblemDetailWithFieldError() throws Exception {
        RegisterRequest body = new RegisterRequest();
        body.setUsername("alice");
        body.setPassword("short");   // < 8 chars → @Size violation
        body.setName("Alice");
        body.setEmail("alice@example.com");
        body.setPostalAddress("10 Downing St");

        mvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation Failed"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.type").value("https://ordermanagement.example/errors/validation"))
                .andExpect(jsonPath("$.errors.password").exists());
    }

    @Test
    void register_invalidEmail_returns400() throws Exception {
        RegisterRequest body = new RegisterRequest();
        body.setUsername("alice");
        body.setPassword("s3cret-password");
        body.setName("Alice");
        body.setEmail("not-an-email");
        body.setPostalAddress("10 Downing St");

        mvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.email").exists());
    }

    @Test
    void register_duplicateUsername_surfacesAs409ProblemDetail() throws Exception {
        RegisterRequest body = new RegisterRequest();
        body.setUsername("alice");
        body.setPassword("s3cret-password");
        body.setName("Alice");
        body.setEmail("alice@example.com");
        body.setPostalAddress("10 Downing St");

        when(authService.register(any(RegisterRequest.class)))
                .thenThrow(new BusinessRuleException("Username already taken"));

        mvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(body)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Business Rule Violated"))
                .andExpect(jsonPath("$.detail").value("Username already taken"));
    }

    // =============================================================
    // POST /auth/login
    // =============================================================

    @Test
    void login_validBody_returns200WithTokens() throws Exception {
        LoginRequest body = new LoginRequest();
        body.setUsername("operator");
        body.setPassword("password123");

        when(authService.login(any(LoginRequest.class)))
                .thenReturn(new AuthResponse("jwt.access", "raw-refresh", 900, "operator", Role.OPERATOR, null, null));

        mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("OPERATOR"));
    }

    @Test
    void login_blankUsername_returns400() throws Exception {
        LoginRequest body = new LoginRequest();
        body.setUsername("");
        body.setPassword("password123");

        mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.username").exists());
    }

    @Test
    void login_invalidCredentials_surfacesAs401() throws Exception {
        LoginRequest body = new LoginRequest();
        body.setUsername("operator");
        body.setPassword("wrong");

        when(authService.login(any(LoginRequest.class)))
                .thenThrow(new UnauthorizedException("Invalid credentials"));

        mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(body)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Unauthorized"))
                .andExpect(jsonPath("$.detail").value("Invalid credentials"));
    }

    // =============================================================
    // POST /auth/refresh + /auth/logout
    // =============================================================

    @Test
    void refresh_validBody_returns200() throws Exception {
        RefreshRequest body = new RefreshRequest();
        body.setRefreshToken("some-refresh");

        when(authService.refresh(anyString()))
                .thenReturn(new AuthResponse("new.jwt", "new-raw", 900, "alice", Role.CUSTOMER, "CUST001", "Alice Example"));

        mvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("new.jwt"));
    }

    @Test
    void logout_validBody_returns204() throws Exception {
        RefreshRequest body = new RefreshRequest();
        body.setRefreshToken("some-refresh");

        mvc.perform(post("/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(body)))
                .andExpect(status().isNoContent());
    }

    @Test
    void malformedJson_returns400ProblemDetail() throws Exception {
        mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Malformed Request"));
    }
}
