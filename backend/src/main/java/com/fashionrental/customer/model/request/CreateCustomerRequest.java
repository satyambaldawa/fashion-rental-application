package com.fashionrental.customer.model.request;

import com.fashionrental.customer.Customer;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record CreateCustomerRequest(
        @NotBlank String name,

        @NotBlank
        @Pattern(regexp = "^[6-9]\\d{9}$", message = "Enter a valid 10-digit Indian mobile number")
        String phone,

        String address,

        @NotNull Customer.CustomerType customerType,

        String organizationName
) {}
