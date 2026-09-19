package com.fashionrental.config;

import com.fashionrental.invoice.InvoiceRepository;
import com.fashionrental.invoice.ReturnService;
import com.fashionrental.invoice.model.response.InvoiceResponse;
import com.fashionrental.receipt.Receipt;
import com.fashionrental.receipt.ReceiptMapper;
import com.fashionrental.receipt.ReceiptRepository;
import com.fashionrental.receipt.model.response.ReceiptResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PublicController.class)
@Import({SecurityConfig.class, SecurityErrorHandler.class})
class PublicControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReceiptRepository receiptRepository;

    @MockitoBean
    private ReceiptMapper receiptMapper;

    @MockitoBean
    private InvoiceRepository invoiceRepository;

    @MockitoBean
    private ReturnService returnService;

    @MockitoBean
    private JwtConfig jwtConfig;

    private ReceiptResponse receiptResponse(UUID id, String shareToken) {
        return new ReceiptResponse(
                id, "R-0001", shareToken, UUID.randomUUID(), "Jane Doe", "9876543210",
                OffsetDateTime.now(), OffsetDateTime.now().plusDays(2), 2, 1000, null, 0,
                500, 1500, "ACTIVE", null, List.of(), OffsetDateTime.now()
        );
    }

    // ─── GET /api/public/receipts/{shareToken} ───────────────────────────────

    @Test
    void should_return_receipt_for_valid_share_token_without_authentication() throws Exception {
        Receipt receipt = new Receipt();
        UUID id = UUID.randomUUID();
        when(receiptRepository.findByShareToken("valid-token")).thenReturn(Optional.of(receipt));
        when(receiptMapper.toReceiptResponse(receipt)).thenReturn(receiptResponse(id, "valid-token"));

        mockMvc.perform(get("/api/public/receipts/{shareToken}", "valid-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.shareToken").value("valid-token"));
    }

    @Test
    void should_return_404_for_unknown_receipt_share_token() throws Exception {
        when(receiptRepository.findByShareToken("unknown-token")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/public/receipts/{shareToken}", "unknown-token"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    // ─── GET /api/public/invoices/{shareToken} ───────────────────────────────

    @Test
    void should_return_invoice_for_valid_share_token_without_authentication() throws Exception {
        com.fashionrental.invoice.Invoice invoice = new com.fashionrental.invoice.Invoice();
        UUID id = UUID.randomUUID();
        InvoiceResponse response = new InvoiceResponse(
                id, "INV-0001", "valid-invoice-token", UUID.randomUUID(), "R-0001",
                UUID.randomUUID(), "Jane Doe", "9876543210", OffsetDateTime.now(),
                1000, null, 0, 500, 0, 0, 500, 500, "REFUND", "CASH", null, null,
                List.of(), OffsetDateTime.now()
        );
        when(invoiceRepository.findByShareToken("valid-invoice-token")).thenReturn(Optional.of(invoice));
        when(returnService.toInvoiceResponse(invoice)).thenReturn(response);

        mockMvc.perform(get("/api/public/invoices/{shareToken}", "valid-invoice-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.shareToken").value("valid-invoice-token"));
    }

    @Test
    void should_return_404_for_unknown_invoice_share_token() throws Exception {
        when(invoiceRepository.findByShareToken("unknown-token")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/public/invoices/{shareToken}", "unknown-token"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }
}
