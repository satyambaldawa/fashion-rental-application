package com.fashionrental.configuration.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// A blank or absent upiVpa disables UPI at checkout; the payee name is then ignored.
public record UpdatePaymentSettingsRequest(
        @Size(max = 256)
        @Pattern(regexp = UPI_VPA_PATTERN, message = "Enter a valid UPI ID, e.g. shopname@ybl")
        String upiVpa,
        @Size(max = 100) String upiPayeeName
) {
    // Blank is allowed (it means "disable UPI"); otherwise handle@psp as NPCI defines it.
    public static final String UPI_VPA_PATTERN = "^$|^[a-zA-Z0-9._-]{2,256}@[a-zA-Z]{2,64}$";

    public boolean disablesUpi() {
        return upiVpa == null || upiVpa.isBlank();
    }

    // Jackson would otherwise serialise this as a request property and springdoc would publish it
    @JsonIgnore
    @AssertTrue(message = "Payee name is required when a UPI ID is set")
    public boolean isPayeeNamePresentWhenUpiEnabled() {
        return disablesUpi() || (upiPayeeName != null && !upiPayeeName.isBlank());
    }
}
