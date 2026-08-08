package com.fashionrental.customer.model.response;

import com.fashionrental.customer.Customer;

import java.util.List;
import java.util.UUID;

public record CustomerDetailResponse(
        UUID id,
        String name,
        String phone,
        String address,
        Customer.CustomerType customerType,
        String organizationName,
        int outstandingDeposit,
        List<CustomerReceiptResponse> receipts
) {}
