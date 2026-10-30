package com.fashionrental.receipt;

// Deliberately separate from Invoice.PaymentMethod (which also allows OTHER): a receipt is
// paid in full by exactly one of these at checkout.
public enum ReceiptPaymentMethod {
    CASH, UPI
}
