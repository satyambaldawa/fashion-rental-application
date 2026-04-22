package com.fashionrental.receipt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fashionrental.common.exception.ConflictException;
import com.fashionrental.common.exception.ValidationException;
import com.fashionrental.receipt.model.request.CheckoutLineItem;
import com.fashionrental.receipt.model.request.CheckoutRequest;
import com.fashionrental.receipt.model.response.CheckoutPreviewResponse;
import com.fashionrental.receipt.model.response.ReceiptLineItemResponse;
import com.fashionrental.receipt.model.response.ReceiptResponse;
import com.fashionrental.receipt.model.response.ReceiptSummaryResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ReceiptController.class)
class ReceiptControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockitoBean CheckoutService checkoutService;
    @MockitoBean ReceiptService receiptService;

    // Required by JwtAuthFilter and SecurityConfig wiring in @WebMvcTest
    @MockitoBean com.fashionrental.config.JwtConfig jwtConfig;
    @MockitoBean org.springframework.security.core.userdetails.UserDetailsService userDetailsService;

    private static final OffsetDateTime START = OffsetDateTime.parse("2026-04-21T10:00:00+05:30");
    private static final OffsetDateTime END = OffsetDateTime.parse("2026-04-24T10:00:00+05:30");

    private CheckoutRequest validRequest() {
        return new CheckoutRequest(
                UUID.randomUUID(), START, END,
                List.of(new CheckoutLineItem(UUID.randomUUID(), 1)),
                null
        );
    }

    private CheckoutPreviewResponse previewResponse() {
        return new CheckoutPreviewResponse(
                true,
                List.of(),
                3, 600, 1000, 1600,
                List.of()
        );
    }

    private ReceiptResponse receiptResponse() {
        return new ReceiptResponse(
                UUID.randomUUID(), "R-20260421-001",
                UUID.randomUUID(), "Ravi Sharma", "9876543210",
                START, END, 3, 600, 1000, 1600,
                "GIVEN", null,
                List.of(new ReceiptLineItemResponse(
                        UUID.randomUUID(), UUID.randomUUID(), "Blue Sherwani",
                        "M", "COSTUME", null,
                        1, 200, 1000, 600, 1000
                )),
                OffsetDateTime.now()
        );
    }

    // ─── POST /api/checkout/preview ──────────────────────────────────────────

    @Test
    @WithMockUser
    void should_return_200_from_preview() throws Exception {
        when(checkoutService.preview(any())).thenReturn(previewResponse());

        mockMvc.perform(post("/api/checkout/preview").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.allAvailable").value(true))
                .andExpect(jsonPath("$.data.grandTotal").value(1600));
    }

    // ─── POST /api/receipts ───────────────────────────────────────────────────

    @Test
    @WithMockUser
    void should_return_201_when_receipt_created() throws Exception {
        when(checkoutService.createReceipt(any())).thenReturn(receiptResponse());

        mockMvc.perform(post("/api/receipts").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.receiptNumber").value("R-20260421-001"));
    }

    @Test
    @WithMockUser
    void should_return_409_on_conflict() throws Exception {
        when(checkoutService.createReceipt(any()))
                .thenThrow(new ConflictException("'Blue Sherwani' no longer has enough units available. Please review your order."));

        mockMvc.perform(post("/api/receipts").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("Blue Sherwani")));
    }

    @Test
    @WithMockUser
    void should_return_400_when_end_before_start() throws Exception {
        when(checkoutService.createReceipt(any()))
                .thenThrow(new ValidationException("endDatetime must be after startDatetime"));

        mockMvc.perform(post("/api/receipts").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("endDatetime")));
    }

    @Test
    @WithMockUser
    void should_return_400_when_items_empty() throws Exception {
        CheckoutRequest invalidRequest = new CheckoutRequest(
                UUID.randomUUID(), START, END, List.of(), null
        );

        mockMvc.perform(post("/api/receipts").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void should_return_401_when_unauthenticated() throws Exception {
        mockMvc.perform(post("/api/receipts").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isUnauthorized());
    }

    // ─── GET /api/receipts ────────────────────────────────────────────────────

    @Test
    @WithMockUser
    void should_return_200_for_receipts_list() throws Exception {
        ReceiptSummaryResponse summary = new ReceiptSummaryResponse(
                UUID.randomUUID(), "R-20260421-001",
                "Ravi Sharma", "9876543210",
                List.of("Blue Sherwani \u00d71"),
                START, END, 3, 600, 1000, 1600,
                "GIVEN", false, null
        );

        when(receiptService.listReceipts(any(), any())).thenReturn(List.of(summary));

        mockMvc.perform(get("/api/receipts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].receiptNumber").value("R-20260421-001"))
                .andExpect(jsonPath("$.data[0].isOverdue").value(false));
    }
}
