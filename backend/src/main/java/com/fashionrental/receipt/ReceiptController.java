package com.fashionrental.receipt;

import com.fashionrental.common.response.ApiResponse;
import com.fashionrental.receipt.model.request.CheckoutRequest;
import com.fashionrental.receipt.model.response.CheckoutPreviewResponse;
import com.fashionrental.receipt.model.response.ReceiptResponse;
import com.fashionrental.receipt.model.response.ReceiptSummaryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Receipts", description = "Checkout and receipt management")
@RestController
public class ReceiptController {

    private final CheckoutService checkoutService;
    private final ReceiptService receiptService;

    public ReceiptController(CheckoutService checkoutService, ReceiptService receiptService) {
        this.checkoutService = checkoutService;
        this.receiptService = receiptService;
    }

    @Operation(summary = "Preview checkout totals and availability before creating a receipt")
    @PostMapping("/api/checkout/preview")
    public ResponseEntity<ApiResponse<CheckoutPreviewResponse>> previewCheckout(
            @Valid @RequestBody CheckoutRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.ok(checkoutService.preview(request)));
    }

    @Operation(summary = "Create a new rental receipt")
    @PostMapping("/api/receipts")
    public ResponseEntity<ApiResponse<ReceiptResponse>> createReceipt(
            @Valid @RequestBody CheckoutRequest request
    ) {
        ReceiptResponse created = checkoutService.createReceipt(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(created));
    }

    @Operation(summary = "List receipts filtered by status or overdue flag")
    @GetMapping("/api/receipts")
    public ResponseEntity<ApiResponse<List<ReceiptSummaryResponse>>> listReceipts(
            @RequestParam(required = false) Receipt.Status status,
            @RequestParam(required = false) Boolean overdue
    ) {
        return ResponseEntity.ok(ApiResponse.ok(receiptService.listReceipts(status, overdue)));
    }

    @Operation(summary = "Get receipt details by ID")
    @GetMapping("/api/receipts/{id}")
    public ResponseEntity<ApiResponse<ReceiptResponse>> getReceipt(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(receiptService.getReceipt(id)));
    }
}
