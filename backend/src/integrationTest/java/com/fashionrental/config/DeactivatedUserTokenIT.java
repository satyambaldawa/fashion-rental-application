package com.fashionrental.config;

import com.fashionrental.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves a deactivated user's already-issued token stops working on its very next request (#189),
 * through the real filter chain and a real database.
 */
@AutoConfigureMockMvc
class DeactivatedUserTokenIT extends AbstractIntegrationTest {

    private static final String USERNAME = "deactivation-it-staff";
    private static final String PASSWORD = "a-strong-test-password";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private AppUserRepository appUserRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private UUID userId;

    @AfterEach
    void removeTestUser() {
        if (userId != null) {
            appUserRepository.deleteById(userId);
        }
    }

    @Test
    void should_stop_accepting_a_token_on_the_next_request_after_its_user_is_deactivated() throws Exception {
        AppUser user = new AppUser();
        user.setUsername(USERNAME);
        user.setPasswordHash(passwordEncoder.encode(PASSWORD));
        user.setRole(AppUser.Role.EXECUTIVE);
        user = appUserRepository.save(user);
        userId = user.getId();
        String token = loginAndGetToken();

        mockMvc.perform(get("/api/items").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        user.setActive(false);
        appUserRepository.save(user);

        mockMvc.perform(get("/api/items").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    private String loginAndGetToken() throws Exception {
        String body = objectMapper.writeValueAsString(java.util.Map.of("username", USERNAME, "password", PASSWORD));
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("token").asText();
    }
}
