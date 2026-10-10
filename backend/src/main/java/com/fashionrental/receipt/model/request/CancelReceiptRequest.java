package com.fashionrental.receipt.model.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fashionrental.receipt.Receipt;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CancelReceiptRequest(
        @NotNull(message = "Cancellation reason is required") Receipt.CancellationReason reason,
        @Size(max = 500, message = "Reason detail must be at most 500 characters") String reasonDetail
) {
    // Jackson would otherwise serialise this as a request property and springdoc would publish it
    @JsonIgnore
    @AssertTrue(message = "Please describe the reason when choosing Other")
    public boolean isDetailPresentWhenReasonIsOther() {
        return reason != Receipt.CancellationReason.OTHER
                || (reasonDetail != null && !reasonDetail.isBlank());
    }
}
