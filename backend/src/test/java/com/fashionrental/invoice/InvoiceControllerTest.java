package com.fashionrental.invoice;

import com.fashionrental.config.JwtConfig;
import com.fashionrental.config.SecurityConfig;
import com.fashionrental.config.SecurityErrorHandler;
import com.fashionrental.invoice.model.request.ProcessReturnRequest;
import com.fashionrental.invoice.model.request.ReturnLineItem;
import com.fashionrental.invoice.model.response.InvoiceResponse;
import com.fashionrental.invoice.model.response.ReturnPreviewResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InvoiceController.class)
@Import({SecurityConfig.class, SecurityErrorHandler.class})
class InvoiceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ReturnService returnService;

    @MockitoBean
    private JwtConfig jwtConfig;

    private ProcessReturnRequest returnRequest() {
        return new ProcessReturnRequest(
                OffsetDateTime.now(),
                List.of(new ReturnLineItem(UUID.randomUUID(), false, null, null)),
                "CASH", null, null
        );
    }

    private InvoiceResponse invoiceResponse(UUID id) {
        return new InvoiceResponse(
                id, "INV-0001", "share-token", UUID.randomUUID(), "R-0001",
                UUID.randomUUID(), "Jane Doe", "9876543210", OffsetDateTime.now(),
                1000, null, 0, 500, 0, 0, 500, 500, "REFUND", "CASH", null, null,
                List.of(), OffsetDateTime.now()
        );
    }

    // ─── POST /api/receipts/{id}/return/preview ──────────────────────────────

    @Test
    @WithMockUser
    void should_return_preview_without_persisting() throws Exception {
        UUID receiptId = UUID.randomUUID();
        ReturnPreviewResponse preview = new ReturnPreviewResponse(0, 0, 0, 500, 500, "REFUND", List.of());
        when(returnService.previewReturn(eq(receiptId), any())).thenReturn(preview);

        mockMvc.perform(post("/api/receipts/{id}/return/preview", receiptId).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(returnRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.transactionType").value("REFUND"))
                .andExpect(jsonPath("$.data.depositToReturn").value(500));
    }

    @Test
    void should_return_401_when_previewing_return_without_authentication() throws Exception {
        UUID receiptId = UUID.randomUUID();

        mockMvc.perform(post("/api/receipts/{id}/return/preview", receiptId).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(returnRequest())))
                .andExpect(status().isUnauthorized());
    }

    // ─── POST /api/receipts/{id}/return ──────────────────────────────────────

    @Test
    @WithMockUser
    void should_process_return_and_return_201_with_invoice() throws Exception {
        UUID receiptId = UUID.randomUUID();
        UUID invoiceId = UUID.randomUUID();
        when(returnService.processReturn(eq(receiptId), any())).thenReturn(invoiceResponse(invoiceId));

        mockMvc.perform(post("/api/receipts/{id}/return", receiptId).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(returnRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value(invoiceId.toString()));
    }

    @Test
    @WithMockUser
    void should_return_400_when_return_datetime_is_missing() throws Exception {
        UUID receiptId = UUID.randomUUID();
        String body = "{\"lineItems\":[]}";

        mockMvc.perform(post("/api/receipts/{id}/return", receiptId).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    // ─── GET /api/invoices/{id} ───────────────────────────────────────────────

    @Test
    @WithMockUser
    void should_return_invoice_by_id() throws Exception {
        UUID invoiceId = UUID.randomUUID();
        when(returnService.getInvoice(invoiceId)).thenReturn(invoiceResponse(invoiceId));

        mockMvc.perform(get("/api/invoices/{id}", invoiceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.invoiceNumber").value("INV-0001"));
    }

    @Test
    void should_return_401_when_getting_invoice_without_authentication() throws Exception {
        mockMvc.perform(get("/api/invoices/{id}", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }
}
