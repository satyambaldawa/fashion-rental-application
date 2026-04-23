package com.fashionrental.invoice;

import com.fashionrental.common.response.ApiResponse;
import com.fashionrental.invoice.model.request.ProcessReturnRequest;
import com.fashionrental.invoice.model.response.InvoiceResponse;
import com.fashionrental.invoice.model.response.ReturnPreviewResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@Tag(name = "Invoices")
public class InvoiceController {

    private final ReturnService returnService;

    public InvoiceController(ReturnService returnService) {
        this.returnService = returnService;
    }

    @PostMapping("/api/receipts/{id}/return/preview")
    @Operation(summary = "Preview return invoice without saving")
    public ApiResponse<ReturnPreviewResponse> previewReturn(
            @PathVariable UUID id,
            @RequestBody @Valid ProcessReturnRequest request
    ) {
        return ApiResponse.ok(returnService.previewReturn(id, request));
    }

    @PostMapping("/api/receipts/{id}/return")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Process return and generate invoice")
    public ApiResponse<InvoiceResponse> processReturn(
            @PathVariable UUID id,
            @RequestBody @Valid ProcessReturnRequest request
    ) {
        return ApiResponse.ok(returnService.processReturn(id, request));
    }

    @GetMapping("/api/invoices/{id}")
    @Operation(summary = "Get invoice by ID")
    public ApiResponse<InvoiceResponse> getInvoice(@PathVariable UUID id) {
        return ApiResponse.ok(returnService.getInvoice(id));
    }
}
