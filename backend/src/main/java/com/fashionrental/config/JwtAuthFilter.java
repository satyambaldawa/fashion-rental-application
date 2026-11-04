package com.fashionrental.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fashionrental.common.response.ApiResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthFilter.class);

    static final String USER_LOOKUP_UNAVAILABLE_MESSAGE = "Service temporarily unavailable. Please try again.";

    private final JwtConfig jwtConfig;
    private final AppUserRepository appUserRepository;
    private final ObjectMapper objectMapper;

    public JwtAuthFilter(JwtConfig jwtConfig, AppUserRepository appUserRepository, ObjectMapper objectMapper) {
        this.jwtConfig = jwtConfig;
        this.appUserRepository = appUserRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(7);

        if (!jwtConfig.validateToken(token)) {
            filterChain.doFilter(request, response);
            return;
        }

        String username = jwtConfig.extractUsername(token);
        String role = jwtConfig.extractRole(token);

        if (username != null && role != null
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            boolean active;
            try {
                active = isActiveUser(username);
            } catch (DataAccessException e) {
                // Thrown inside the filter chain, this would bypass GlobalExceptionHandler and reach
                // the client as the container's raw 500 page instead of the ApiResponse envelope.
                log.error("Could not verify the active status of user '{}' for {} {}",
                        username, request.getMethod(), request.getRequestURI(), e);
                writeServiceUnavailable(response);
                return;
            }
            if (active) {
                var authority = new SimpleGrantedAuthority("ROLE_" + role);
                var authToken = new UsernamePasswordAuthenticationToken(
                        username, null, List.of(authority));
                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authToken);
            }
        }

        filterChain.doFilter(request, response);
    }

    // A token stays cryptographically valid after the user is deactivated, so the account
    // is checked on every request that presents a valid token. The role still comes from the
    // token: re-reading it from the DB is a separate follow-up.
    private boolean isActiveUser(String username) {
        return appUserRepository.findByUsernameAndIsActiveTrue(username).isPresent();
    }

    private void writeServiceUnavailable(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), ApiResponse.error(USER_LOOKUP_UNAVAILABLE_MESSAGE));
    }
}
