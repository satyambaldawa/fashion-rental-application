package com.fashionrental.configuration.model;

public record PaymentSettingsResponse(
        boolean upiEnabled,
        String upiVpa,
        String upiPayeeName
) {}
