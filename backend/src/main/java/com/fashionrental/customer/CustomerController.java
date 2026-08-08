package com.fashionrental.customer;

import com.fashionrental.common.response.ApiResponse;
import com.fashionrental.customer.model.request.CreateCustomerRequest;
import com.fashionrental.customer.model.request.UpdateCustomerRequest;
import com.fashionrental.customer.model.response.CustomerDetailResponse;
import com.fashionrental.customer.model.response.CustomerResponse;
import com.fashionrental.customer.model.response.CustomerSummaryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Customers", description = "Customer management")
@RestController
@RequestMapping("/api/customers")
public class CustomerController {

    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    @Operation(summary = "Register a new customer")
    @PostMapping
    public ResponseEntity<ApiResponse<CustomerResponse>> createCustomer(
            @Valid @RequestBody CreateCustomerRequest request
    ) {
        CustomerResponse created = customerService.createCustomer(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(created));
    }

    @Operation(summary = "Search customers by phone or name")
    @GetMapping
    public ResponseEntity<ApiResponse<List<CustomerSummaryResponse>>> searchCustomers(
            @RequestParam(required = false) String phone,
            @RequestParam(required = false) String name
    ) {
        List<CustomerSummaryResponse> results = customerService.searchCustomers(phone, name);
        return ResponseEntity.ok(ApiResponse.ok(results));
    }

    @Operation(summary = "Get customer details by ID")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<CustomerResponse>> getCustomer(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(customerService.getCustomer(id)));
    }

    @Operation(summary = "Get customer's full rental history, receipts, and outstanding deposit")
    @GetMapping("/{id}/history")
    public ResponseEntity<ApiResponse<CustomerDetailResponse>> getCustomerHistory(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(customerService.getCustomerHistory(id)));
    }

    @Operation(summary = "Update an existing customer's details")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<CustomerResponse>> updateCustomer(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateCustomerRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.ok(customerService.updateCustomer(id, request)));
    }
}
