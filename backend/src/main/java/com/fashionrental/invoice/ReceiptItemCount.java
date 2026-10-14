package com.fashionrental.invoice;

import java.util.UUID;

public record ReceiptItemCount(UUID receiptId, long itemCount) {}
