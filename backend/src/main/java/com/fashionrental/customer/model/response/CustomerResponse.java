package com.fashionrental.customer.model.response;

import com.fashionrental.customer.Customer;

import java.time.OffsetDateTime;
import java.util.UUID;

public record CustomerResponse(
        UUID id,
        String name,
        String phone,
        String address,
        Customer.CustomerType customerType,
        String organizationName,
        Boolean isActive,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {}
