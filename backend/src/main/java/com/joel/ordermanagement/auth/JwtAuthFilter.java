package com.joel.ordermanagement.auth;

import com.auth0.jwt.interfaces.DecodedJWT;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Per-request JWT parser. Extracts the bearer token from the {@code Authorization}
 * header, verifies it, and — on success — populates {@link SecurityContextHolder}
 * with an {@link org.springframework.security.core.Authentication}. Downstream
 * URL rules ({@code hasRole(...)}) then decide if the request can proceed.
 * <p>
 * If there's no token, or it's invalid, we simply do nothing — Spring's later
 * {@code AuthorizationFilter} sees an unauthenticated context and returns 401/403
 * for anything that isn't on the public allow-list.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";

    private final JwtService jwtService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain)
            throws ServletException, IOException {

        String header = request.getHeader(HEADER);
        if (header != null && header.startsWith(PREFIX)) {
            String token = header.substring(PREFIX.length());
            jwtService.verify(token).ifPresent(jwt -> populateContext(jwt, request));
        }

        chain.doFilter(request, response);
    }

    private void populateContext(DecodedJWT jwt, HttpServletRequest request) {
        String role = jwt.getClaim("role").asString();
        String username = jwt.getClaim("username").asString();
        String userId = jwt.getSubject();

        // Spring Security convention: role names are prefixed ROLE_ when
        // stored as GrantedAuthority but NOT when referenced via hasRole("X").
        var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role));

        var auth = new UsernamePasswordAuthenticationToken(userId, null, authorities);
        auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

        SecurityContextHolder.getContext().setAuthentication(auth);

        // Stash username on the request for downstream logging if useful.
        request.setAttribute("auth.username", username);
    }
}
