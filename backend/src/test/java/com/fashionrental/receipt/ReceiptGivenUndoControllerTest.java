package com.fashionrental.receipt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fashionrental.common.exception.ConflictException;
import com.fashionrental.config.JwtConfig;
import com.fashionrental.config.SecurityConfig;
import com.fashionrental.config.SecurityErrorHandler;
import com.fashionrental.receipt.model.response.ReceiptResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// A separate class from ReceiptControllerTest (which mocks SecurityConfig away) so the real
// filter chain can be exercised here, the same pattern as ReceiptCancellationControllerTest:
// OWNER-only access is the core acceptance criterion for this endpoint (#179).
@WebMvcTest(ReceiptController.class)
@Import({SecurityConfig.class, SecurityErrorHandler.class})
class ReceiptGivenUndoControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private CheckoutService checkoutService;
    @MockitoBean private ReceiptService receiptService;
    @MockitoBean private ReceiptCancellationService receiptCancellationService;
    @MockitoBean private ReceiptGivenService receiptGivenService;
    @MockitoBean private JwtConfig jwtConfig;

    private static final OffsetDateTime START = OffsetDateTime.parse("2026-04-21T10:00:00+05:30");
    private static final OffsetDateTime END = OffsetDateTime.parse("2026-04-24T10:00:00+05:30");

    private ReceiptResponse generatedReceiptResponse(UUID id) {
        return new ReceiptResponse(
                id, "R-20260421-001", "tok3nABCD12",
                UUID.randomUUID(), "Ravi Sharma", "9876543210",
                START, END, 3, 600, null, 0, 1000, 1600,
                "GENERATED", null, null, List.of(), OffsetDateTime.now(), null
        );
    }

    @Test
    @WithMockUser(username = "owner", roles = "OWNER")
    void owner_can_undo_mark_as_given() throws Exception {
        UUID id = UUID.randomUUID();
        when(receiptGivenService.undoGiven(eq(id), eq("owner"))).thenReturn(generatedReceiptResponse(id));

        mockMvc.perform(post("/api/receipts/{id}/undo-give", id).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("GENERATED"));

        verify(receiptGivenService).undoGiven(eq(id), eq("owner"));
    }

    @Test
    @WithMockUser(roles = "EXECUTIVE")
    void executive_is_forbidden_from_undoing_mark_as_given() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(post("/api/receipts/{id}/undo-give", id).with(csrf()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Access denied"));

        verify(receiptGivenService, never()).undoGiven(any(), any());
    }

    @Test
    void unauthenticated_request_is_rejected() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(post("/api/receipts/{id}/undo-give", id).with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "OWNER")
    void should_return_409_when_service_rejects_the_undo() throws Exception {
        UUID id = UUID.randomUUID();
        when(receiptGivenService.undoGiven(eq(id), any()))
                .thenThrow(new ConflictException("Receipt R-0001 is RETURNED and its hand-over can no longer be undone"));

        mockMvc.perform(post("/api/receipts/{id}/undo-give", id).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("Receipt R-0001 is RETURNED and its hand-over can no longer be undone"));
    }
}
