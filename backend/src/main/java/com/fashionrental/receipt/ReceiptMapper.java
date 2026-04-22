package com.fashionrental.receipt;

import com.fashionrental.receipt.model.response.ReceiptLineItemResponse;
import com.fashionrental.receipt.model.response.ReceiptResponse;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ReceiptMapper {

    public ReceiptResponse toReceiptResponse(Receipt receipt) {
        List<ReceiptLineItemResponse> lineItems = receipt.getLineItems().stream()
                .map(this::toLineItemResponse)
                .toList();

        return new ReceiptResponse(
                receipt.getId(),
                receipt.getReceiptNumber(),
                receipt.getCustomer().getId(),
                receipt.getCustomer().getName(),
                receipt.getCustomer().getPhone(),
                receipt.getStartDatetime(),
                receipt.getEndDatetime(),
                receipt.getRentalDays(),
                receipt.getTotalRent(),
                receipt.getTotalDeposit(),
                receipt.getGrandTotal(),
                receipt.getStatus().name(),
                receipt.getNotes(),
                lineItems,
                receipt.getCreatedAt()
        );
    }

    private ReceiptLineItemResponse toLineItemResponse(ReceiptLineItem li) {
        return new ReceiptLineItemResponse(
                li.getId(),
                li.getItem().getId(),
                li.getItem().getName(),
                li.getQuantity(),
                li.getRateSnapshot(),
                li.getDepositSnapshot(),
                li.getLineRent(),
                li.getLineDeposit()
        );
    }
}
