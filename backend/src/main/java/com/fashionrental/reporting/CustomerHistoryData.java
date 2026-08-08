package com.fashionrental.reporting;

import com.fashionrental.customer.model.response.CustomerReceiptResponse;

import java.util.List;

public record CustomerHistoryData(
        int outstandingDeposit,
        List<CustomerReceiptResponse> receipts
) {}
