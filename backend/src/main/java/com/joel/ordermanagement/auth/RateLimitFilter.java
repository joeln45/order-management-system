package com.joel.ordermanagement.auth;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-IP token-bucket rate limit on {@code /auth/login} and {@code /auth/register}.
 * <p>
 * Each IP gets a bucket of 10 tokens that refills at 10 tokens per minute.
 * A legitimate user who mistypes their password a few times is fine; a brute
 * force script hits 429 after 10 attempts and has to wait for the refill.
 * <p>
 * Buckets live in an in-memory {@link ConcurrentHashMap}. Fine for a single
 * node; a multi-node deployment would swap this for the Bucket4j Redis
 * back-end (out of scope for this phase).
 */
@Slf4j
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final int CAPACITY = 10;
    private static final Duration REFILL_PERIOD = Duration.ofMinutes(1);

    private final Map<String, Bucket> bucketsByIp = new ConcurrentHashMap<>();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !(path.equals("/auth/login") || path.equals("/auth/register"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain)
            throws ServletException, IOException {

        String ip = resolveClientIp(request);
        Bucket bucket = bucketsByIp.computeIfAbsent(ip, this::newBucket);

        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        if (probe.isConsumed()) {
            response.setHeader("X-RateLimit-Remaining", String.valueOf(probe.getRemainingTokens()));
            chain.doFilter(request, response);
            return;
        }

        long retryAfterSeconds = probe.getNanosToWaitForRefill() / 1_000_000_000L;
        log.warn("Rate limit hit for IP {} on {}", ip, request.getRequestURI());
        writeTooManyRequests(response, request.getRequestURI(), retryAfterSeconds);
    }

    private Bucket newBucket(String ip) {
        return Bucket.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(CAPACITY)
                        .refillGreedy(CAPACITY, REFILL_PERIOD)
                        .build())
                .build();
    }

    /** Prefer X-Forwarded-For when present (reverse proxy / Docker). */
    private String resolveClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            // First IP in the chain is the originating client.
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    /** Write an RFC 7807 ProblemDetail manually (we're outside the @RestControllerAdvice chain). */
    private void writeTooManyRequests(HttpServletResponse response, String path, long retryAfterSec)
            throws IOException {
        response.setStatus(429);  // HTTP 429 Too Many Requests (RFC 6585)
        response.setHeader("Retry-After", String.valueOf(Math.max(1, retryAfterSec)));
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        String body = String.format("""
                {"type":"https://ordermanagement.example/errors/rate-limit",\
                "title":"Too Many Requests",\
                "status":429,\
                "detail":"Rate limit exceeded; retry in %d seconds",\
                "instance":"%s",\
                "timestamp":"%s"}""",
                Math.max(1, retryAfterSec), path, Instant.now());
        response.getWriter().write(body);
    }
}
