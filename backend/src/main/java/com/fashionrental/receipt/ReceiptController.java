package com.fashionrental.receipt;

import com.fashionrental.common.response.ApiResponse;
import com.fashionrental.receipt.model.request.CancelReceiptRequest;
import com.fashionrental.receipt.model.request.CheckoutPreviewRequest;
import com.fashionrental.receipt.model.request.CheckoutRequest;
import com.fashionrental.receipt.model.request.EligibleCouponsRequest;
import com.fashionrental.receipt.model.response.CheckoutPreviewResponse;
import com.fashionrental.receipt.model.response.EligibleCouponResponse;
import com.fashionrental.receipt.model.response.ReceiptResponse;
import com.fashionrental.receipt.model.response.ReceiptSummaryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

@Tag(name = "Receipts", description = "Checkout and receipt management")
@RestController
public class ReceiptController {

    private final CheckoutService checkoutService;
    private final ReceiptService receiptService;
    private final ReceiptCancellationService receiptCancellationService;
    private final ReceiptGivenService receiptGivenService;

    public ReceiptController(
            CheckoutService checkoutService,
            ReceiptService receiptService,
            ReceiptCancellationService receiptCancellationService,
            ReceiptGivenService receiptGivenService
    ) {
        this.checkoutService = checkoutService;
        this.receiptService = receiptService;
        this.receiptCancellationService = receiptCancellationService;
        this.receiptGivenService = receiptGivenService;
    }

    @Operation(summary = "Preview checkout totals and availability before creating a receipt")
    @PostMapping("/api/checkout/preview")
    public ResponseEntity<ApiResponse<CheckoutPreviewResponse>> previewCheckout(
            @Valid @RequestBody CheckoutPreviewRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.ok(checkoutService.preview(request)));
    }

    @Operation(summary = "List coupons currently eligible for this cart (any authenticated staff role)")
    @PostMapping("/api/checkout/eligible-coupons")
    public ResponseEntity<ApiResponse<List<EligibleCouponResponse>>> eligibleCoupons(
            @Valid @RequestBody EligibleCouponsRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.ok(checkoutService.eligibleCoupons(request)));
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

    @Operation(summary = "Cancel a Generated (not yet given) receipt (owner only); refunds the full amount collected")
    @PostMapping("/api/receipts/{id}/cancel")
    public ResponseEntity<ApiResponse<ReceiptResponse>> cancelReceipt(
            @PathVariable UUID id, @Valid @RequestBody CancelReceiptRequest request, Principal principal
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
                receiptCancellationService.cancelReceipt(id, request, principal.getName())));
    }

    @Operation(summary = "Mark a Generated receipt as Given — the dress has been handed to the customer")
    @PostMapping("/api/receipts/{id}/give")
    public ResponseEntity<ApiResponse<ReceiptResponse>> markReceiptGiven(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(receiptGivenService.markGiven(id)));
    }

    @Operation(summary = "Undo a mistaken Mark as Given — move a Given receipt back to Generated before its booked start (owner only)")
    @PostMapping("/api/receipts/{id}/undo-give")
    public ResponseEntity<ApiResponse<ReceiptResponse>> undoReceiptGiven(@PathVariable UUID id, Principal principal) {
        return ResponseEntity.ok(ApiResponse.ok(receiptGivenService.undoGiven(id, principal.getName())));
    }
}
