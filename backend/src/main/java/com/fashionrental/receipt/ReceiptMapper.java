package com.fashionrental.receipt;

import com.fashionrental.configuration.Coupon;
import com.fashionrental.inventory.Item;
import com.fashionrental.receipt.model.response.EligibleCouponResponse;
import com.fashionrental.receipt.model.response.ReceiptCancellationResponse;
import com.fashionrental.receipt.model.response.ReceiptLineItemResponse;
import com.fashionrental.receipt.model.response.ReceiptResponse;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ReceiptMapper {

    public EligibleCouponResponse toEligibleCouponResponse(AppliedDiscount discount) {
        Coupon coupon = discount.coupon();
        return new EligibleCouponResponse(
                discount.code(),
                coupon.getDiscountType().name(),
                coupon.getValue(),
                coupon.getMinSubtotal(),
                coupon.getValidTo(),
                discount.amount()
        );
    }

    public ReceiptResponse toReceiptResponse(Receipt receipt) {
        return buildReceiptResponse(receipt, toCancellationResponse(receipt));
    }

    // The reason and the staff username are internal audit data and should not appear on
    // the unauthenticated share link (plan §10, decision D5) — status alone still shows CANCELLED.
    public ReceiptResponse toPublicReceiptResponse(Receipt receipt) {
        return buildReceiptResponse(receipt, null);
    }

    private ReceiptResponse buildReceiptResponse(Receipt receipt, ReceiptCancellationResponse cancellation) {
        List<ReceiptLineItemResponse> lineItems = receipt.getLineItems().stream()
                .map(this::toLineItemResponse)
                .toList();

        return new ReceiptResponse(
                receipt.getId(),
                receipt.getReceiptNumber(),
                receipt.getShareToken(),
                receipt.getCustomer().getId(),
                receipt.getCustomer().getName(),
                receipt.getCustomer().getPhone(),
                receipt.getStartDatetime(),
                receipt.getEndDatetime(),
                receipt.getRentalDays(),
                receipt.getTotalRent(),
                receipt.getCouponCode(),
                receipt.getDiscountAmount(),
                receipt.getTotalDeposit(),
                receipt.getGrandTotal(),
                receipt.getStatus().name(),
                receipt.getNotes(),
                lineItems,
                receipt.getCreatedAt(),
                cancellation
        );
    }

    private ReceiptCancellationResponse toCancellationResponse(Receipt receipt) {
        if (receipt.getStatus() != Receipt.Status.CANCELLED) {
            return null;
        }
        return new ReceiptCancellationResponse(
                receipt.getCancelledAt(),
                receipt.getCancelledBy() != null ? receipt.getCancelledBy().getUsername() : null,
                receipt.getCancellationReason() != null ? receipt.getCancellationReason().name() : null,
                receipt.getCancellationReasonDetail()
        );
    }

    private ReceiptLineItemResponse toLineItemResponse(ReceiptLineItem li) {
        Item item = li.getItem();
        return new ReceiptLineItemResponse(
                li.getId(),
                item.getId(),
                item.getName(),
                item.getPrimaryThumbnailUrl(),
                item.getSize(),
                item.getCategory() != null ? item.getCategory().name() : null,
                item.getDescription(),
                li.getQuantity(),
                li.getRateSnapshot(),
                li.getDepositSnapshot(),
                li.getLineRent(),
                li.getLineDeposit(),
                item.getPurchaseRate()
        );
    }
}
