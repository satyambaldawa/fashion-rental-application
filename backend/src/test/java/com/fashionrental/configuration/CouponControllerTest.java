package com.fashionrental.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fashionrental.config.SecurityConfig;
import com.fashionrental.config.SecurityErrorHandler;
import com.fashionrental.configuration.model.CouponResponse;
import com.fashionrental.configuration.model.CreateCouponRequest;
import com.fashionrental.configuration.model.SetCouponStatusRequest;
import com.fashionrental.configuration.model.UpdateCouponRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Exercises the real SecurityConfig filter chain (not a mocked one) so that a future
// edit to the /api/config/** matcher would actually break this test — the #66 AC is
// "EXECUTIVE blocked", and placement-based enforcement deserves a regression guard.
@WebMvcTest(CouponController.class)
@Import({SecurityConfig.class, SecurityErrorHandler.class})
class CouponControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private CouponService couponService;

    // Required by JwtAuthFilter and SecurityConfig wiring in @WebMvcTest
    @MockitoBean private com.fashionrental.config.JwtConfig jwtConfig;

    private static final OffsetDateTime FROM = OffsetDateTime.parse("2026-06-01T00:00:00+05:30");
    private static final OffsetDateTime TO = OffsetDateTime.parse("2026-06-30T23:59:59+05:30");

    private CouponResponse response() {
        return new CouponResponse(
                UUID.randomUUID(), "SAVE20", "PERCENT", 20, null,
                FROM, TO, null, 0, true, OffsetDateTime.now(), OffsetDateTime.now());
    }

    // ─── OWNER: every verb succeeds ─────────────────────────────────────────

    @Test
    @WithMockUser(roles = "OWNER")
    void owner_can_list_coupons() throws Exception {
        when(couponService.listCoupons(false)).thenReturn(List.of(response()));

        mockMvc.perform(get("/api/config/coupons"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].code").value("SAVE20"));
    }

    @Test
    @WithMockUser(roles = "OWNER")
    void owner_can_create_a_coupon() throws Exception {
        when(couponService.createCoupon(any())).thenReturn(response());

        CreateCouponRequest request = new CreateCouponRequest("SAVE20", "PERCENT", 20, null, FROM, TO, null);

        mockMvc.perform(post("/api/config/coupons").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @WithMockUser(roles = "OWNER")
    void owner_can_update_a_coupon() throws Exception {
        UUID id = UUID.randomUUID();
        when(couponService.updateCoupon(any(), any())).thenReturn(response());

        UpdateCouponRequest request = new UpdateCouponRequest("PERCENT", 20, null, FROM, TO, null);

        mockMvc.perform(put("/api/config/coupons/" + id).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @WithMockUser(roles = "OWNER")
    void owner_can_set_coupon_status() throws Exception {
        UUID id = UUID.randomUUID();
        when(couponService.setStatus(any(), any())).thenReturn(response());

        mockMvc.perform(patch("/api/config/coupons/" + id + "/status").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SetCouponStatusRequest(false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    // ─── EXECUTIVE: every verb is forbidden ─────────────────────────────────

    @Test
    @WithMockUser(roles = "EXECUTIVE")
    void executive_is_forbidden_from_listing_coupons() throws Exception {
        mockMvc.perform(get("/api/config/coupons"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "EXECUTIVE")
    void executive_is_forbidden_from_creating_a_coupon() throws Exception {
        CreateCouponRequest request = new CreateCouponRequest("SAVE20", "PERCENT", 20, null, FROM, TO, null);

        mockMvc.perform(post("/api/config/coupons").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "EXECUTIVE")
    void executive_is_forbidden_from_updating_a_coupon() throws Exception {
        UpdateCouponRequest request = new UpdateCouponRequest("PERCENT", 20, null, FROM, TO, null);

        mockMvc.perform(put("/api/config/coupons/" + UUID.randomUUID()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "EXECUTIVE")
    void executive_is_forbidden_from_changing_coupon_status() throws Exception {
        mockMvc.perform(patch("/api/config/coupons/" + UUID.randomUUID() + "/status").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SetCouponStatusRequest(false))))
                .andExpect(status().isForbidden());
    }

    @Test
    void unauthenticated_request_is_rejected() throws Exception {
        mockMvc.perform(get("/api/config/coupons"))
                .andExpect(status().isUnauthorized());
    }
}
