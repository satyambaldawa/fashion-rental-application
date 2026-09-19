package com.fashionrental.configuration;

import com.fashionrental.config.JwtConfig;
import com.fashionrental.config.SecurityConfig;
import com.fashionrental.config.SecurityErrorHandler;
import com.fashionrental.configuration.model.LateFeeRuleItem;
import com.fashionrental.configuration.model.LateFeeRuleResponse;
import com.fashionrental.configuration.model.UpdateLateFeeRulesRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ConfigController.class)
@Import({SecurityConfig.class, SecurityErrorHandler.class})
class ConfigControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ConfigService configService;

    @MockitoBean
    private JwtConfig jwtConfig;

    // ─── GET /api/config/late-fee-rules ──────────────────────────────────────

    @Test
    @WithMockUser(roles = "OWNER")
    void should_return_configured_late_fee_rules() throws Exception {
        LateFeeRuleResponse rule = new LateFeeRuleResponse(
                UUID.randomUUID(), 0, 24, BigDecimal.valueOf(1.0), 0, true, "Within 24 hours"
        );
        when(configService.getLateFeeRules()).thenReturn(List.of(rule));

        mockMvc.perform(get("/api/config/late-fee-rules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].label").value("Within 24 hours"));
    }

    @Test
    void should_return_401_when_unauthenticated() throws Exception {
        mockMvc.perform(get("/api/config/late-fee-rules"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "EXECUTIVE")
    void should_return_403_when_executive_reads_config() throws Exception {
        mockMvc.perform(get("/api/config/late-fee-rules"))
                .andExpect(status().isForbidden());
    }

    // ─── PUT /api/config/late-fee-rules ──────────────────────────────────────

    @Test
    @WithMockUser(roles = "OWNER")
    void should_replace_late_fee_rules_when_request_is_valid() throws Exception {
        LateFeeRuleItem item = new LateFeeRuleItem(null, 0, 24, BigDecimal.valueOf(1.5), 0, true);
        UpdateLateFeeRulesRequest request = new UpdateLateFeeRulesRequest(List.of(item));
        LateFeeRuleResponse saved = new LateFeeRuleResponse(
                UUID.randomUUID(), 0, 24, BigDecimal.valueOf(1.5), 0, true, "Within 24 hours"
        );
        when(configService.updateLateFeeRules(any(UpdateLateFeeRulesRequest.class))).thenReturn(List.of(saved));

        mockMvc.perform(put("/api/config/late-fee-rules").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)));
    }

    @Test
    @WithMockUser(roles = "OWNER")
    void should_return_400_when_rules_list_is_empty() throws Exception {
        UpdateLateFeeRulesRequest request = new UpdateLateFeeRulesRequest(List.of());

        mockMvc.perform(put("/api/config/late-fee-rules").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @WithMockUser(roles = "OWNER")
    void should_return_400_when_penalty_multiplier_is_below_minimum() throws Exception {
        LateFeeRuleItem item = new LateFeeRuleItem(null, 0, 24, BigDecimal.valueOf(0.0), 0, true);
        UpdateLateFeeRulesRequest request = new UpdateLateFeeRulesRequest(List.of(item));

        mockMvc.perform(put("/api/config/late-fee-rules").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }
}
