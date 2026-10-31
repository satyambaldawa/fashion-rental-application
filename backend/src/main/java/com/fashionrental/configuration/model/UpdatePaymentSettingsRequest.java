package com.fashionrental.configuration.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// A blank or absent upiVpa disables UPI at checkout; the payee name is then ignored.
// Values are trimmed on construction, so validation always sees what will be stored.
public record UpdatePaymentSettingsRequest(
        @Size(max = 256)
        @Pattern(regexp = UPI_VPA_PATTERN, message = "Enter a valid UPI ID, e.g. shopname@ybl")
        String upiVpa,
        @Size(max = 100) String upiPayeeName
) {
    // handle@psp as NPCI defines it; a null (blank) VPA skips this check and disables UPI.
    public static final String UPI_VPA_PATTERN = "^[a-zA-Z0-9._-]{2,256}@[a-zA-Z]{2,64}$";

    public UpdatePaymentSettingsRequest {
        upiVpa = trimToNull(upiVpa);
        upiPayeeName = trimToNull(upiPayeeName);
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public boolean disablesUpi() {
        return upiVpa == null;
    }

    // Jackson would otherwise serialise this as a request property and springdoc would publish it
    @JsonIgnore
    @AssertTrue(message = "Payee name is required when a UPI ID is set")
    public boolean isPayeeNamePresentWhenUpiEnabled() {
        return disablesUpi() || upiPayeeName != null;
    }
}
