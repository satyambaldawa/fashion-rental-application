package com.fashionrental.receipt;

import java.util.UUID;

public record ReceiptItemCount(UUID receiptId, long itemCount) {}
