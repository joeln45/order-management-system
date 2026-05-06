package com.joel.ordermanagement.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Central exception → HTTP translator. Every response produced here follows
 * <a href="https://datatracker.ietf.org/doc/html/rfc7807">RFC 7807 ProblemDetail</a>:
 * a single, stable JSON envelope that frontends can parse generically.
 * <p>
 * Handlers are ordered from most-specific to most-generic; Spring picks the
 * closest match for any thrown exception.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // ------------------------------------------------------------
    // Domain exceptions
    // ------------------------------------------------------------

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ProblemDetail> handleNotFound(NotFoundException ex, HttpServletRequest req) {
        return problem(HttpStatus.NOT_FOUND, "Not Found", ex.getMessage(), req, "not-found");
    }

    @ExceptionHandler(BusinessRuleException.class)
    public ResponseEntity<ProblemDetail> handleBusinessRule(BusinessRuleException ex, HttpServletRequest req) {
        return problem(HttpStatus.CONFLICT, "Business Rule Violated", ex.getMessage(), req, "business-rule");
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ProblemDetail> handleUnauthorized(UnauthorizedException ex, HttpServletRequest req) {
        return problem(HttpStatus.UNAUTHORIZED, "Unauthorized", ex.getMessage(), req, "unauthorized");
    }

    // ------------------------------------------------------------
    // Spring / validation exceptions
    // ------------------------------------------------------------

    /** Bean validation failures (@Valid): builds a field-by-field error map. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest req) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(fe ->
                errors.put(fe.getField(), fe.getDefaultMessage()));

        ProblemDetail body = baseProblem(HttpStatus.BAD_REQUEST, "Validation Failed",
                "One or more fields are invalid", req, "validation");
        body.setProperty("errors", errors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    /** Malformed JSON body (missing braces, wrong types, etc.). */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> handleUnreadable(HttpMessageNotReadableException ex, HttpServletRequest req) {
        return problem(HttpStatus.BAD_REQUEST, "Malformed Request",
                "Request body is missing or not valid JSON", req, "malformed-request");
    }

    /** Spring Security: authenticated but lacks the required role. */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> handleAccessDenied(AccessDeniedException ex, HttpServletRequest req) {
        return problem(HttpStatus.FORBIDDEN, "Forbidden", "You do not have permission to access this resource",
                req, "forbidden");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetail> handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest req) {
        return problem(HttpStatus.BAD_REQUEST, "Bad Request", ex.getMessage(), req, "bad-request");
    }

    // ------------------------------------------------------------
    // Last-resort catch-all
    // ------------------------------------------------------------

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(Exception ex, HttpServletRequest req) {
        // Log full stack trace server-side, but never leak it to the client.
        log.error("Unhandled exception on {} {}", req.getMethod(), req.getRequestURI(), ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error",
                "An unexpected error occurred", req, "internal-error");
    }

    // ------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------

    private ResponseEntity<ProblemDetail> problem(HttpStatus status, String title, String detail,
                                                   HttpServletRequest req, String slug) {
        return ResponseEntity.status(status).body(baseProblem(status, title, detail, req, slug));
    }

    private ProblemDetail baseProblem(HttpStatus status, String title, String detail,
                                       HttpServletRequest req, String slug) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(status, detail);
        body.setTitle(title);
        body.setType(URI.create("https://ordermanagement.example/errors/" + slug));
        body.setInstance(URI.create(req.getRequestURI()));
        body.setProperty("timestamp", Instant.now().toString());
        return body;
    }
}
