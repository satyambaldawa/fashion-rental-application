package com.fashionrental.receipt.model.response;

import java.time.OffsetDateTime;

public record ReceiptCancellationResponse(
        OffsetDateTime cancelledAt,
        String cancelledByUsername,
        String reason,
        String reasonDetail
) {}
