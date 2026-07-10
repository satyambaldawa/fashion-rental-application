package com.fashionrental.config;

import com.fashionrental.common.exception.ResourceNotFoundException;
import com.fashionrental.common.response.ApiResponse;
import com.fashionrental.invoice.InvoiceRepository;
import com.fashionrental.invoice.ReturnService;
import com.fashionrental.invoice.model.response.InvoiceResponse;
import com.fashionrental.receipt.ReceiptMapper;
import com.fashionrental.receipt.ReceiptRepository;
import com.fashionrental.receipt.model.response.ReceiptResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public")
@Tag(name = "Public")
public class PublicController {

    private final ReceiptRepository receiptRepository;
    private final ReceiptMapper receiptMapper;
    private final InvoiceRepository invoiceRepository;
    private final ReturnService returnService;

    public PublicController(
            ReceiptRepository receiptRepository,
            ReceiptMapper receiptMapper,
            InvoiceRepository invoiceRepository,
            ReturnService returnService
    ) {
        this.receiptRepository = receiptRepository;
        this.receiptMapper = receiptMapper;
        this.invoiceRepository = invoiceRepository;
        this.returnService = returnService;
    }

    @GetMapping("/receipts/{shareToken}")
    @Operation(summary = "Get receipt by share token (no auth required)")
    public ApiResponse<ReceiptResponse> getReceiptByShareToken(@PathVariable String shareToken) {
        return receiptRepository.findByShareToken(shareToken)
                .map(receiptMapper::toReceiptResponse)
                .map(ApiResponse::ok)
                .orElseThrow(() -> new ResourceNotFoundException("Receipt not found"));
    }

    @GetMapping("/invoices/{shareToken}")
    @Operation(summary = "Get invoice by share token (no auth required)")
    public ApiResponse<InvoiceResponse> getInvoiceByShareToken(@PathVariable String shareToken) {
        return invoiceRepository.findByShareToken(shareToken)
                .map(returnService::toInvoiceResponse)
                .map(ApiResponse::ok)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found"));
    }
}
