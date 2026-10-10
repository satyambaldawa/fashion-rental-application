package com.fashionrental.receipt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fashionrental.config.SecurityConfig;
import com.fashionrental.config.SecurityErrorHandler;
import com.fashionrental.receipt.model.request.CheckoutLineItem;
import com.fashionrental.receipt.model.request.EligibleCouponsRequest;
import com.fashionrental.receipt.model.response.EligibleCouponResponse;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Exercises the real SecurityConfig filter chain (not a mocked one), mirroring
// CouponControllerTest: EXECUTIVE access is the core acceptance criterion for this
// endpoint, and placement under /api/checkout/** (the /api/** authenticated catch-all,
// not /api/config/**) deserves a regression guard against a future matcher change.
@WebMvcTest(ReceiptController.class)
@Import({SecurityConfig.class, SecurityErrorHandler.class})
class CheckoutEligibleCouponsControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private CheckoutService checkoutService;
    @MockitoBean private ReceiptService receiptService;
    @MockitoBean private ReceiptCancellationService receiptCancellationService;

    // Required by JwtAuthFilter and SecurityConfig wiring in @WebMvcTest
    @MockitoBean private com.fashionrental.config.JwtConfig jwtConfig;

    private static final OffsetDateTime START = OffsetDateTime.parse("2026-04-21T10:00:00+05:30");
    private static final OffsetDateTime END = OffsetDateTime.parse("2026-04-24T10:00:00+05:30");

    private EligibleCouponsRequest validRequest() {
        return new EligibleCouponsRequest(
                START, END, List.of(new CheckoutLineItem(UUID.randomUUID(), 1)), List.of());
    }

    private EligibleCouponResponse response() {
        return new EligibleCouponResponse("SAVE20", "PERCENT", 20, null, END, 60);
    }

    @Test
    @WithMockUser(roles = "OWNER")
    void owner_can_list_eligible_coupons() throws Exception {
        when(checkoutService.eligibleCoupons(any())).thenReturn(List.of(response()));

        mockMvc.perform(post("/api/checkout/eligible-coupons").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].code").value("SAVE20"));
    }

    @Test
    @WithMockUser(roles = "EXECUTIVE")
    void executive_can_list_eligible_coupons() throws Exception {
        when(checkoutService.eligibleCoupons(any())).thenReturn(List.of(response()));

        mockMvc.perform(post("/api/checkout/eligible-coupons").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].code").value("SAVE20"));
    }

    @Test
    void unauthenticated_request_is_rejected() throws Exception {
        mockMvc.perform(post("/api/checkout/eligible-coupons").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "OWNER")
    void should_return_400_when_no_lines() throws Exception {
        EligibleCouponsRequest emptyRequest = new EligibleCouponsRequest(START, END, List.of(), List.of());

        mockMvc.perform(post("/api/checkout/eligible-coupons").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(emptyRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", org.hamcrest.Matchers.containsString("at least one item")));
    }

    @Test
    @WithMockUser(roles = "OWNER")
    void should_return_an_empty_array_not_an_error_when_none_eligible() throws Exception {
        when(checkoutService.eligibleCoupons(any())).thenReturn(List.of());

        mockMvc.perform(post("/api/checkout/eligible-coupons").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }
}
