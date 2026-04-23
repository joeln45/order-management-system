package com.joel.ordermanagement.auth;

import com.joel.ordermanagement.auth.dto.AuthResponse;
import com.joel.ordermanagement.auth.dto.LoginRequest;
import com.joel.ordermanagement.auth.dto.RegisterRequest;
import com.joel.ordermanagement.customer.Customer;
import com.joel.ordermanagement.customer.CustomerRepository;
import com.joel.ordermanagement.exception.BusinessRuleException;
import com.joel.ordermanagement.exception.UnauthorizedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AuthService}. All collaborators are mocked. Key things
 * these tests lock down:
 * <ul>
 *   <li>Passwords go through {@link PasswordEncoder} (never stored plain).</li>
 *   <li>Refresh tokens are stored only as SHA-256 hashes (never the raw value).</li>
 *   <li>{@code refresh()} rotates — the old token row is deleted before the new
 *       pair is issued.</li>
 *   <li>Wrong password / unknown user / disabled user all collapse to the same
 *       "Invalid credentials" response so we don't leak user existence.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private CustomerRepository customerRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtService jwtService;

    @InjectMocks private AuthService authService;

    private User customerUser;

    @BeforeEach
    void setUp() {
        customerUser = new User("alice", "bcrypt-hash", Role.CUSTOMER);
        customerUser.setId("user-1");

        // Common JwtService stubs — some tests short-circuit before using them,
        // so mark lenient() to keep Mockito's strict stubbing detector quiet.
        lenient().when(jwtService.issueAccessToken(any(User.class))).thenReturn("fake.access.jwt");
        lenient().when(jwtService.generateRefreshTokenRaw()).thenReturn("fake-raw-refresh-42");
        lenient().when(jwtService.getAccessTtl()).thenReturn(Duration.ofMinutes(15));
        lenient().when(jwtService.getRefreshTtl()).thenReturn(Duration.ofDays(7));
    }

    // =============================================================
    // register
    // =============================================================

    @Nested
    @DisplayName("register")
    class Register {

        @Test
        @DisplayName("hashes the password, saves User + Customer, issues tokens")
        void register_happyPath() {
            RegisterRequest req = registerRequest("alice", "s3cret-password");
            when(userRepository.existsByUsername("alice")).thenReturn(false);
            when(passwordEncoder.encode("s3cret-password")).thenReturn("bcrypt-hash");
            when(userRepository.save(any(User.class))).thenAnswer(inv -> {
                User u = inv.getArgument(0);
                u.setId("user-1");
                return u;
            });
            when(customerRepository.save(any(Customer.class))).thenAnswer(inv -> inv.getArgument(0));

            AuthResponse response = authService.register(req);

            // password never leaks into storage raw
            ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(userCaptor.capture());
            assertThat(userCaptor.getValue().getPasswordHash()).isEqualTo("bcrypt-hash");
            assertThat(userCaptor.getValue().getRole()).isEqualTo(Role.CUSTOMER);

            // customer row carries the profile data and the user link
            ArgumentCaptor<Customer> custCaptor = ArgumentCaptor.forClass(Customer.class);
            verify(customerRepository).save(custCaptor.capture());
            Customer saved = custCaptor.getValue();
            assertThat(saved.getName()).isEqualTo("Alice Example");
            assertThat(saved.getEmail()).isEqualTo("alice@example.com");
            assertThat(saved.getUser()).isNotNull();
            assertThat(saved.getId()).startsWith("CUST-");

            // refresh token stored as SHA-256 hash, never raw
            ArgumentCaptor<RefreshToken> rtCaptor = ArgumentCaptor.forClass(RefreshToken.class);
            verify(refreshTokenRepository).save(rtCaptor.capture());
            assertThat(rtCaptor.getValue().getTokenHash())
                    .isEqualTo(AuthService.sha256Hex("fake-raw-refresh-42"))
                    .doesNotContain("fake-raw-refresh-42");  // raw value NOT in DB
            assertThat(rtCaptor.getValue().getExpiresAt()).isAfter(LocalDateTime.now());

            // response shape
            assertThat(response.getAccessToken()).isEqualTo("fake.access.jwt");
            assertThat(response.getRefreshToken()).isEqualTo("fake-raw-refresh-42");
            assertThat(response.getUsername()).isEqualTo("alice");
            assertThat(response.getRole()).isEqualTo(Role.CUSTOMER);
            assertThat(response.getAccessTokenExpiresInSeconds()).isEqualTo(900L);
        }

        @Test
        @DisplayName("rejects duplicate username with BusinessRuleException")
        void register_duplicateUsername_throws() {
            when(userRepository.existsByUsername("alice")).thenReturn(true);

            assertThatThrownBy(() -> authService.register(registerRequest("alice", "x")))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("already taken");

            verify(userRepository, never()).save(any());
            verify(customerRepository, never()).save(any());
            verify(refreshTokenRepository, never()).save(any());
        }
    }

    // =============================================================
    // login
    // =============================================================

    @Nested
    @DisplayName("login")
    class Login {

        @Test
        @DisplayName("accepts valid credentials and issues tokens")
        void login_happyPath() {
            when(userRepository.findByUsername("alice")).thenReturn(Optional.of(customerUser));
            when(passwordEncoder.matches("correct", "bcrypt-hash")).thenReturn(true);

            AuthResponse response = authService.login(loginRequest("alice", "correct"));

            assertThat(response.getAccessToken()).isEqualTo("fake.access.jwt");
            assertThat(response.getUsername()).isEqualTo("alice");
            verify(refreshTokenRepository).save(any(RefreshToken.class));
        }

        @Test
        @DisplayName("rejects unknown username with UnauthorizedException (no user-enumeration leak)")
        void login_unknownUser_throwsUnauthorized() {
            when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.login(loginRequest("ghost", "x")))
                    .isInstanceOf(UnauthorizedException.class)
                    .hasMessageContaining("Invalid credentials");  // generic, no mention of username
        }

        @Test
        @DisplayName("rejects wrong password with the same 'Invalid credentials' message")
        void login_wrongPassword_throwsUnauthorized() {
            when(userRepository.findByUsername("alice")).thenReturn(Optional.of(customerUser));
            when(passwordEncoder.matches("wrong", "bcrypt-hash")).thenReturn(false);

            assertThatThrownBy(() -> authService.login(loginRequest("alice", "wrong")))
                    .isInstanceOf(UnauthorizedException.class)
                    .hasMessageContaining("Invalid credentials");

            verify(refreshTokenRepository, never()).save(any());
        }

        @Test
        @DisplayName("rejects a disabled user even with the right password")
        void login_disabledUser_throwsUnauthorized() {
            customerUser.setEnabled(false);
            when(userRepository.findByUsername("alice")).thenReturn(Optional.of(customerUser));

            assertThatThrownBy(() -> authService.login(loginRequest("alice", "correct")))
                    .isInstanceOf(UnauthorizedException.class);

            verify(refreshTokenRepository, never()).save(any());
        }
    }

    // =============================================================
    // refresh
    // =============================================================

    @Nested
    @DisplayName("refresh")
    class Refresh {

        @Test
        @DisplayName("rotates: deletes old row, issues a new token pair")
        void refresh_happyPath_rotates() {
            String raw = "old-refresh-abc";
            String hash = AuthService.sha256Hex(raw);
            RefreshToken existing = new RefreshToken(
                    customerUser, hash, LocalDateTime.now().plusDays(1));
            when(refreshTokenRepository.findByTokenHash(hash)).thenReturn(Optional.of(existing));

            AuthResponse response = authService.refresh(raw);

            // old row must be deleted before the new pair is saved
            verify(refreshTokenRepository).delete(existing);
            verify(refreshTokenRepository).save(any(RefreshToken.class));
            assertThat(response.getAccessToken()).isEqualTo("fake.access.jwt");
            assertThat(response.getRefreshToken()).isEqualTo("fake-raw-refresh-42");
        }

        @Test
        @DisplayName("unknown refresh token → 401 Unauthorized (no rotation)")
        void refresh_unknown_throwsUnauthorized() {
            when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.refresh("bogus"))
                    .isInstanceOf(UnauthorizedException.class)
                    .hasMessageContaining("Invalid refresh token");

            verify(refreshTokenRepository, never()).save(any());
        }

        @Test
        @DisplayName("expired refresh token is deleted and rejected")
        void refresh_expired_deletesAndThrows() {
            String raw = "expired-refresh";
            String hash = AuthService.sha256Hex(raw);
            RefreshToken stale = new RefreshToken(
                    customerUser, hash, LocalDateTime.now().minusDays(1));  // already expired
            when(refreshTokenRepository.findByTokenHash(hash)).thenReturn(Optional.of(stale));

            assertThatThrownBy(() -> authService.refresh(raw))
                    .isInstanceOf(UnauthorizedException.class)
                    .hasMessageContaining("expired");

            // sweep the dead row as a side effect
            verify(refreshTokenRepository).delete(stale);
            verify(refreshTokenRepository, never()).save(any());
        }

        @Test
        @DisplayName("null or blank refresh token → BusinessRuleException")
        void refresh_blankInput_throwsBusinessRule() {
            assertThatThrownBy(() -> authService.refresh(null))
                    .isInstanceOf(BusinessRuleException.class);
            assertThatThrownBy(() -> authService.refresh(""))
                    .isInstanceOf(BusinessRuleException.class);
            assertThatThrownBy(() -> authService.refresh("   "))
                    .isInstanceOf(BusinessRuleException.class);
        }
    }

    // =============================================================
    // logout
    // =============================================================

    @Nested
    @DisplayName("logout")
    class Logout {

        @Test
        @DisplayName("deletes the refresh token row if present")
        void logout_existingToken_deletes() {
            String raw = "refresh-to-revoke";
            RefreshToken existing = new RefreshToken(
                    customerUser, AuthService.sha256Hex(raw), LocalDateTime.now().plusDays(1));
            when(refreshTokenRepository.findByTokenHash(AuthService.sha256Hex(raw)))
                    .thenReturn(Optional.of(existing));

            authService.logout(raw);

            verify(refreshTokenRepository).delete(existing);
        }

        @Test
        @DisplayName("is idempotent — silently ignores unknown tokens")
        void logout_unknownToken_silent() {
            when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

            authService.logout("never-issued");

            verify(refreshTokenRepository, never()).delete(any());
        }

        @Test
        @DisplayName("is a no-op for null or blank input")
        void logout_blankInput_noOp() {
            authService.logout(null);
            authService.logout("");
            authService.logout("  ");

            verify(refreshTokenRepository, never()).findByTokenHash(anyString());
            verify(refreshTokenRepository, never()).delete(any());
        }
    }

    // =============================================================
    // sha256Hex
    // =============================================================

    @Test
    @DisplayName("sha256Hex is deterministic and produces a 64-char hex string")
    void sha256Hex_deterministicAndHexFormatted() {
        String once = AuthService.sha256Hex("hello");
        String twice = AuthService.sha256Hex("hello");

        assertThat(once).isEqualTo(twice);
        assertThat(once).hasSize(64).matches("[0-9a-f]+");
        assertThat(AuthService.sha256Hex("world")).isNotEqualTo(once);
    }

    // -- helpers -------------------------------------------------

    private static RegisterRequest registerRequest(String username, String password) {
        RegisterRequest r = new RegisterRequest();
        r.setUsername(username);
        r.setPassword(password);
        r.setName("Alice Example");
        r.setEmail("alice@example.com");
        r.setPostalAddress("10 Downing St");
        return r;
    }

    private static LoginRequest loginRequest(String username, String password) {
        LoginRequest r = new LoginRequest();
        r.setUsername(username);
        r.setPassword(password);
        return r;
    }
}
