package com.fashionrental.receipt;

import com.fashionrental.common.exception.ValidationException;
import com.fashionrental.configuration.Coupon;
import com.fashionrental.configuration.CouponRepository;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

// The single home of coupon validation and discount math. Both CheckoutService#preview
// and #createReceipt call resolve() so the two paths can never compute different
// numbers for the same cart. findEligible() shares the same rejectionReason() with
// resolve(), so the eligible-coupons list and the single-code apply path can never
// disagree about which coupons apply to a cart.
@Component
public class CouponDiscountResolver {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);

    private final CouponRepository couponRepository;
    private final Clock clock;

    public CouponDiscountResolver(CouponRepository couponRepository, Clock clock) {
        this.couponRepository = couponRepository;
        this.clock = clock;
    }

    public AppliedDiscount resolve(String rawCode, DiscountableSubtotals subtotals) {
        if (rawCode == null || rawCode.isBlank()) {
            return AppliedDiscount.none();
        }
        String code = rawCode.trim().toUpperCase(Locale.ROOT);

        Coupon coupon = couponRepository.findByCode(code)
                .orElseThrow(() -> new ValidationException("Coupon code '" + code + "' is not valid."));

        OffsetDateTime now = OffsetDateTime.now(clock);
        Optional<String> reason = rejectionReason(coupon, code, subtotals, now);
        if (reason.isPresent()) {
            throw new ValidationException(reason.get());
        }

        int amount = computeDiscountAmount(coupon, subtotals.total());
        return new AppliedDiscount(coupon, code, amount);
    }

    // Lists the active coupons eligible for this cart, in the repository's own
    // newest-first order — not re-sorted by discount size, since that ordering was never
    // asked for and this file is explicitly out of scope for logic changes.
    public List<AppliedDiscount> findEligible(DiscountableSubtotals subtotals) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        return couponRepository.findByIsActiveTrueOrderByCreatedAtDesc().stream()
                .filter(coupon -> rejectionReason(coupon, coupon.getCode(), subtotals, now).isEmpty())
                .map(coupon -> new AppliedDiscount(coupon, coupon.getCode(), computeDiscountAmount(coupon, subtotals.total())))
                .toList();
    }

    private Optional<String> rejectionReason(Coupon coupon, String code, DiscountableSubtotals subtotals, OffsetDateTime now) {
        if (!Boolean.TRUE.equals(coupon.getIsActive())) {
            return Optional.of("Coupon '" + code + "' is no longer active.");
        }
        if (now.isBefore(coupon.getValidFrom())) {
            return Optional.of("Coupon '" + code + "' is not valid yet — it becomes active on "
                    + format(coupon.getValidFrom()) + ".");
        }
        if (now.isAfter(coupon.getValidTo())) {
            return Optional.of("Coupon '" + code + "' expired on " + format(coupon.getValidTo()) + ".");
        }
        if (coupon.getUsageLimit() != null && coupon.getTimesUsed() >= coupon.getUsageLimit()) {
            return Optional.of("Coupon '" + code + "' has reached its usage limit.");
        }
        if (coupon.getMinSubtotal() != null && subtotals.total() < coupon.getMinSubtotal()) {
            return Optional.of("Coupon '" + code + "' requires a minimum " + subtotals.label()
                    + " of ₹" + coupon.getMinSubtotal()
                    + ". This order's " + subtotals.label() + " is ₹" + subtotals.total() + ".");
        }
        return Optional.empty();
    }

    // Floor, then clamp: floor guarantees the discount never exceeds the stated
    // percentage (10% of ₹1505 -> ₹150, not ₹151); the clamp guarantees a FIXED coupon
    // can never push net rent negative. Pure and static so it's directly unit-testable
    // without a repository.
    static int computeDiscountAmount(Coupon coupon, int discountableSubtotal) {
        int amount = coupon.getDiscountType() == Coupon.DiscountType.PERCENT
                ? (discountableSubtotal * coupon.getValue()) / 100
                : coupon.getValue();
        return Math.min(amount, discountableSubtotal);
    }

    private String format(OffsetDateTime dt) {
        return dt.atZoneSameInstant(IST).format(DATE_FORMAT);
    }
}
