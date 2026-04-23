package com.fashionrental.invoice;

import com.fashionrental.common.exception.ValidationException;
import com.fashionrental.common.util.DateTimeUtil;
import com.fashionrental.configuration.LateFeeRule;
import com.fashionrental.invoice.model.request.ReturnLineItem;
import com.fashionrental.receipt.ReceiptLineItem;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;

@Service
public class BillingService {

    private final DateTimeUtil dateTimeUtil;

    public BillingService(DateTimeUtil dateTimeUtil) {
        this.dateTimeUtil = dateTimeUtil;
    }

    /**
     * Calculates the late fee for a single line item.
     * Returns 0 if the return is on time or early.
     * Uses the highest matching tier; falls back to 1.5x if overdue but no tier matches.
     */
    public int calculateLateFee(
            OffsetDateTime endDatetime,
            OffsetDateTime returnDatetime,
            int rateSnapshot,
            int quantity,
            List<LateFeeRule> activeRules
    ) {
        double overdueHours = dateTimeUtil.calculateOverdueHours(endDatetime, returnDatetime);
        if (overdueHours <= 0) return 0;

        double multiplier = activeRules.stream()
                .filter(r -> r.getDurationFromHours() <= overdueHours)
                .max(Comparator.comparingInt(LateFeeRule::getDurationFromHours))
                .map(r -> r.getPenaltyMultiplier().doubleValue())
                .orElse(1.5);

        return (int) Math.round(rateSnapshot * multiplier * quantity);
    }

    /**
     * Calculates the damage cost for a single line item.
     * Ad hoc amount takes priority over percentage if both are provided.
     * Percentage-based damage requires a purchase rate on the item;
     * throws ValidationException if purchase rate is absent.
     */
    public int calculateDamageCost(ReceiptLineItem lineItem, ReturnLineItem returnLine) {
        if (!returnLine.isDamaged()) return 0;

        if (returnLine.adHocDamageAmount() != null) {
            return returnLine.adHocDamageAmount();
        }
        if (returnLine.damagePercentage() != null) {
            Integer purchaseRate = lineItem.getItem().getPurchaseRate();
            if (purchaseRate == null) {
                throw new ValidationException(
                        "Purchase cost not available for '" + lineItem.getItem().getName() +
                        "'. Please enter a fixed damage amount instead."
                );
            }
            return (int) Math.round(purchaseRate * returnLine.damagePercentage() / 100.0);
        }
        return 0;
    }
}
