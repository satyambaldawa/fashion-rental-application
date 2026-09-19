package com.fashionrental.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fashionrental.config.model.CreateUserRequest;
import com.fashionrental.config.model.LoginRequest;
import com.fashionrental.config.model.UpdateUserRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, SecurityErrorHandler.class})
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AuthenticationManager authenticationManager;

    @MockitoBean
    private JwtConfig jwtConfig;

    @MockitoBean
    private AppUserRepository userRepository;

    @MockitoBean
    private PasswordEncoder passwordEncoder;

    private AppUser activeOwner(String username) {
        AppUser user = new AppUser();
        user.setUsername(username);
        user.setPasswordHash("hashed");
        user.setRole(AppUser.Role.OWNER);
        user.setActive(true);
        return user;
    }

    // ─── POST /api/auth/login ────────────────────────────────────────────────

    @Test
    void should_return_token_when_credentials_are_valid() throws Exception {
        AppUser user = activeOwner("owner1");
        when(userRepository.findByUsernameAndIsActiveTrue("owner1")).thenReturn(Optional.of(user));
        when(jwtConfig.generateToken("owner1", "OWNER")).thenReturn("signed-jwt-token");

        mockMvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("owner1", "correct-password"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.token").value("signed-jwt-token"))
                .andExpect(jsonPath("$.data.role").value("OWNER"));

        verify(authenticationManager).authenticate(any());
    }

    @Test
    void should_return_401_when_credentials_are_invalid() throws Exception {
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("Bad credentials"));

        mockMvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("owner1", "wrong-password"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("Invalid username or password"));
    }

    @Test
    void should_return_400_when_login_username_is_blank() throws Exception {
        mockMvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("", "password"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void should_return_500_when_user_deactivated_between_authentication_and_token_issuance() throws Exception {
        // authenticate() succeeded (e.g. race with deactivation) but the follow-up active-user lookup finds nothing
        when(userRepository.findByUsernameAndIsActiveTrue("owner1")).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("owner1", "correct-password"))))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false));
    }

    // ─── POST /api/auth/users ────────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "OWNER")
    void should_return_201_when_user_created_successfully() throws Exception {
        CreateUserRequest request = new CreateUserRequest("newstaff", "password123", AppUser.Role.EXECUTIVE);
        when(userRepository.existsByUsername("newstaff")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("encoded-hash");
        when(userRepository.save(any(AppUser.class))).thenAnswer(invocation -> {
            AppUser saved = invocation.getArgument(0);
            return saved;
        });

        mockMvc.perform(post("/api/auth/users").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.username").value("newstaff"))
                .andExpect(jsonPath("$.data.role").value("EXECUTIVE"));
    }

    @Test
    @WithMockUser(roles = "OWNER")
    void should_return_400_when_creating_user_with_duplicate_username() throws Exception {
        CreateUserRequest request = new CreateUserRequest("existing", "password123", AppUser.Role.EXECUTIVE);
        when(userRepository.existsByUsername("existing")).thenReturn(true);

        mockMvc.perform(post("/api/auth/users").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value(containsString("already exists")));

        verify(userRepository, never()).save(any());
    }

    @Test
    @WithMockUser(roles = "OWNER")
    void should_return_400_when_creating_user_with_short_password() throws Exception {
        CreateUserRequest request = new CreateUserRequest("newstaff", "short", AppUser.Role.EXECUTIVE);

        mockMvc.perform(post("/api/auth/users").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void should_return_401_when_unauthenticated_request_creates_user() throws Exception {
        CreateUserRequest request = new CreateUserRequest("newstaff", "password123", AppUser.Role.EXECUTIVE);

        mockMvc.perform(post("/api/auth/users").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "EXECUTIVE")
    void should_return_403_when_executive_creates_user() throws Exception {
        CreateUserRequest request = new CreateUserRequest("newstaff", "password123", AppUser.Role.EXECUTIVE);

        mockMvc.perform(post("/api/auth/users").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());

        verify(userRepository, never()).save(any());
    }

    // ─── GET /api/auth/users ─────────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "OWNER")
    void should_return_active_users_ordered_by_creation() throws Exception {
        AppUser user = activeOwner("owner1");
        when(userRepository.findAllByIsActiveTrueOrderByCreatedAtAsc()).thenReturn(List.of(user));

        mockMvc.perform(get("/api/auth/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$.data[0].username").value("owner1"));
    }

    // ─── PUT /api/auth/users/{id} ────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "OWNER")
    void should_update_user_role_when_user_exists() throws Exception {
        UUID id = UUID.randomUUID();
        AppUser user = activeOwner("staff1");
        user.setRole(AppUser.Role.EXECUTIVE);
        when(userRepository.findById(id)).thenReturn(Optional.of(user));
        when(userRepository.save(any(AppUser.class))).thenReturn(user);

        UpdateUserRequest request = new UpdateUserRequest(AppUser.Role.OWNER, null);

        mockMvc.perform(put("/api/auth/users/{id}", id).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    @WithMockUser(roles = "OWNER")
    void should_update_password_hash_when_new_password_provided() throws Exception {
        UUID id = UUID.randomUUID();
        AppUser user = activeOwner("staff1");
        when(userRepository.findById(id)).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("newpassword")).thenReturn("new-hash");
        when(userRepository.save(any(AppUser.class))).thenReturn(user);

        UpdateUserRequest request = new UpdateUserRequest(null, "newpassword");

        mockMvc.perform(put("/api/auth/users/{id}", id).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(passwordEncoder).encode("newpassword");
    }

    @Test
    @WithMockUser(roles = "OWNER")
    void should_not_update_password_when_password_is_blank() throws Exception {
        UUID id = UUID.randomUUID();
        AppUser user = activeOwner("staff1");
        when(userRepository.findById(id)).thenReturn(Optional.of(user));
        when(userRepository.save(any(AppUser.class))).thenReturn(user);

        // Blank string is a valid @Size-passing value (min applies to non-blank check inside controller)
        UpdateUserRequest request = new UpdateUserRequest(null, null);

        mockMvc.perform(put("/api/auth/users/{id}", id).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    @WithMockUser(roles = "OWNER")
    void should_return_404_when_updating_nonexistent_user() throws Exception {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.empty());

        UpdateUserRequest request = new UpdateUserRequest(AppUser.Role.OWNER, null);

        mockMvc.perform(put("/api/auth/users/{id}", id).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @WithMockUser(roles = "OWNER")
    void should_return_404_when_updating_a_deactivated_user() throws Exception {
        UUID id = UUID.randomUUID();
        AppUser user = activeOwner("staff1");
        user.setActive(false);
        when(userRepository.findById(id)).thenReturn(Optional.of(user));

        UpdateUserRequest request = new UpdateUserRequest(AppUser.Role.OWNER, null);

        mockMvc.perform(put("/api/auth/users/{id}", id).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    // ─── DELETE /api/auth/users/{id} ─────────────────────────────────────────

    @Test
    @WithMockUser(roles = "OWNER")
    void should_deactivate_user_when_user_exists() throws Exception {
        UUID id = UUID.randomUUID();
        AppUser user = activeOwner("staff1");
        when(userRepository.findById(id)).thenReturn(Optional.of(user));
        when(userRepository.save(any(AppUser.class))).thenReturn(user);

        mockMvc.perform(delete("/api/auth/users/{id}", id).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(userRepository).save(argThat(saved -> !saved.isActive()));
    }

    @Test
    @WithMockUser(roles = "OWNER")
    void should_return_404_when_deactivating_nonexistent_user() throws Exception {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.empty());

        mockMvc.perform(delete("/api/auth/users/{id}", id).with(csrf()))
                .andExpect(status().isNotFound());
    }
}
