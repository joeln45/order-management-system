package com.joel.ordermanagement.auth;

import com.joel.ordermanagement.auth.dto.AuthResponse;
import com.joel.ordermanagement.auth.dto.LoginRequest;
import com.joel.ordermanagement.auth.dto.RegisterRequest;
import com.joel.ordermanagement.customer.Customer;
import com.joel.ordermanagement.customer.CustomerRepository;
import com.joel.ordermanagement.exception.BusinessRuleException;
import com.joel.ordermanagement.exception.UnauthorizedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Registration, login, refresh and logout. Refresh tokens are stored as
 * SHA-256 hashes, never in the clear, and refresh rotates: the old row is
 * deleted before a new pair is issued.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final CustomerRepository customerRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    // Create User + Customer in a single transaction.
    @Transactional
    public AuthResponse register(RegisterRequest req) {
        // @Valid in the controller has already checked all the fields.
        if (userRepository.existsByUsername(req.getUsername())) {
            throw new BusinessRuleException("Username already taken");
        }

        User user = userRepository.save(new User(
                req.getUsername(),
                passwordEncoder.encode(req.getPassword()),
                Role.CUSTOMER));

        Customer customer = new Customer();
        customer.setId("CUST-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        customer.setName(req.getName());
        customer.setEmail(req.getEmail());
        customer.setPostalAddress(req.getPostalAddress());
        customer.setUser(user);
        customerRepository.save(customer);

        log.info("Registered new customer: username={}, customerId={}", user.getUsername(), customer.getId());
        return issueTokens(user);
    }

    @Transactional
    public AuthResponse login(LoginRequest req) {
        User user = userRepository.findByUsername(req.getUsername())
                .orElseThrow(() -> new UnauthorizedException("Invalid credentials"));

        if (!user.isEnabled() || !passwordEncoder.matches(req.getPassword(), user.getPasswordHash())) {
            throw new UnauthorizedException("Invalid credentials");
        }

        return issueTokens(user);
    }

    // Rotate the refresh token and mint a new access token.
    @Transactional
    public AuthResponse refresh(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new BusinessRuleException("Missing refreshToken");
        }

        String hash = sha256Hex(rawRefreshToken);
        RefreshToken stored = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new UnauthorizedException("Invalid refresh token"));

        if (stored.isExpired()) {
            refreshTokenRepository.delete(stored);
            throw new UnauthorizedException("Refresh token expired");
        }

        // Invalidate the presented refresh token before issuing a new pair.
        User user = stored.getUser();
        refreshTokenRepository.delete(stored);

        return issueTokens(user);
    }

    // Revoke a refresh token. No-op if it's missing or already gone.
    @Transactional
    public void logout(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) return;
        refreshTokenRepository.findByTokenHash(sha256Hex(rawRefreshToken))
                .ifPresent(refreshTokenRepository::delete);
    }

    private AuthResponse issueTokens(User user) {
        String accessToken = jwtService.issueAccessToken(user);
        String rawRefresh = jwtService.generateRefreshTokenRaw();

        RefreshToken record = new RefreshToken(
                user,
                sha256Hex(rawRefresh),
                LocalDateTime.now().plus(jwtService.getRefreshTtl()));
        refreshTokenRepository.save(record);

        Customer linkedCustomer = customerRepository.findByUser_Username(user.getUsername())
                .orElse(null);

        return new AuthResponse(
                accessToken,
                rawRefresh,
                jwtService.getAccessTtl().toSeconds(),
                user.getUsername(),
                user.getRole(),
                linkedCustomer == null ? null : linkedCustomer.getId(),
                linkedCustomer == null ? null : linkedCustomer.getName());
    }

    /** SHA-256 hex digest. Used to hash refresh tokens before persistence. */
    static String sha256Hex(String raw) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
