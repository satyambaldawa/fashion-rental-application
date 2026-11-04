package com.fashionrental.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtAuthFilterTest {

    @Mock
    private JwtConfig jwtConfig;

    @Mock
    private AppUserRepository appUserRepository;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private JwtAuthFilter filter;

    @BeforeEach
    void setUp() {
        // Mocks aren't injected until after field initializers run, so the filter
        // must be built here rather than at field-declaration time.
        filter = new JwtAuthFilter(jwtConfig, appUserRepository, objectMapper);
    }

    private void givenActiveUser(String username) {
        when(appUserRepository.findByUsernameAndIsActiveTrue(username)).thenReturn(Optional.of(new AppUser()));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void should_continue_chain_without_authentication_when_no_header_present() throws Exception {
        when(request.getHeader("Authorization")).thenReturn(null);

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void should_continue_chain_without_authentication_when_header_is_not_bearer() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Basic dXNlcjpwYXNz");

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void should_continue_chain_without_authentication_when_token_is_invalid() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer bad-token");
        when(jwtConfig.validateToken("bad-token")).thenReturn(false);

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void should_set_authentication_when_token_is_valid() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer good-token");
        when(jwtConfig.validateToken("good-token")).thenReturn(true);
        when(jwtConfig.extractUsername("good-token")).thenReturn("owner1");
        when(jwtConfig.extractRole("good-token")).thenReturn("OWNER");
        givenActiveUser("owner1");

        filter.doFilterInternal(request, response, filterChain);

        var auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isInstanceOf(UsernamePasswordAuthenticationToken.class);
        assertThat(auth.getName()).isEqualTo("owner1");
        assertThat(auth.getAuthorities()).extracting(Object::toString).containsExactly("ROLE_OWNER");
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void should_not_set_authentication_when_the_user_has_been_deactivated() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer good-token");
        when(jwtConfig.validateToken("good-token")).thenReturn(true);
        when(jwtConfig.extractUsername("good-token")).thenReturn("owner1");
        when(jwtConfig.extractRole("good-token")).thenReturn("OWNER");
        when(appUserRepository.findByUsernameAndIsActiveTrue("owner1")).thenReturn(Optional.empty());

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void should_keep_the_role_from_the_token_when_the_database_role_differs() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer good-token");
        when(jwtConfig.validateToken("good-token")).thenReturn(true);
        when(jwtConfig.extractUsername("good-token")).thenReturn("staff1");
        when(jwtConfig.extractRole("good-token")).thenReturn("EXECUTIVE");
        AppUser promotedSince = new AppUser();
        promotedSince.setRole(AppUser.Role.OWNER);
        when(appUserRepository.findByUsernameAndIsActiveTrue("staff1")).thenReturn(Optional.of(promotedSince));

        filter.doFilterInternal(request, response, filterChain);

        var auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth.getAuthorities()).extracting(Object::toString).containsExactly("ROLE_EXECUTIVE");
    }

    @Test
    void should_return_json_error_when_user_lookup_fails() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer good-token");
        when(jwtConfig.validateToken("good-token")).thenReturn(true);
        when(jwtConfig.extractUsername("good-token")).thenReturn("owner1");
        when(jwtConfig.extractRole("good-token")).thenReturn("OWNER");
        when(appUserRepository.findByUsernameAndIsActiveTrue("owner1"))
                .thenThrow(new DataAccessResourceFailureException("connection refused"));
        StringWriter body = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(body));

        filter.doFilterInternal(request, response, filterChain);

        verify(response).setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        JsonNode json = objectMapper.readTree(body.toString());
        assertThat(json.get("success").asBoolean()).isFalse();
        assertThat(json.get("error").asText()).isEqualTo(JwtAuthFilter.USER_LOOKUP_UNAVAILABLE_MESSAGE);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    void should_not_query_the_database_when_the_token_is_invalid() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer bad-token");
        when(jwtConfig.validateToken("bad-token")).thenReturn(false);

        filter.doFilterInternal(request, response, filterChain);

        verifyNoInteractions(appUserRepository);
    }

    @Test
    void should_not_query_the_database_when_no_token_is_presented() throws Exception {
        when(request.getHeader("Authorization")).thenReturn(null);

        filter.doFilterInternal(request, response, filterChain);

        verifyNoInteractions(appUserRepository);
    }

    @Test
    void should_not_set_authentication_when_token_has_no_role_claim() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer good-token");
        when(jwtConfig.validateToken("good-token")).thenReturn(true);
        when(jwtConfig.extractUsername("good-token")).thenReturn("owner1");
        when(jwtConfig.extractRole("good-token")).thenReturn(null);

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void should_not_overwrite_existing_authentication_when_already_set() throws Exception {
        var existing = new UsernamePasswordAuthenticationToken("someone-else", null, java.util.List.of());
        SecurityContextHolder.getContext().setAuthentication(existing);

        when(request.getHeader("Authorization")).thenReturn("Bearer good-token");
        when(jwtConfig.validateToken("good-token")).thenReturn(true);
        when(jwtConfig.extractUsername("good-token")).thenReturn("owner1");
        when(jwtConfig.extractRole("good-token")).thenReturn("OWNER");

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isSameAs(existing);
        verify(filterChain).doFilter(request, response);
    }
}
