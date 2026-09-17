package com.fashionrental.configuration;

import com.fashionrental.common.exception.ConflictException;
import com.fashionrental.common.exception.ResourceNotFoundException;
import com.fashionrental.common.exception.ValidationException;
import com.fashionrental.configuration.model.CouponResponse;
import com.fashionrental.configuration.model.CreateCouponRequest;
import com.fashionrental.configuration.model.SetCouponStatusRequest;
import com.fashionrental.configuration.model.UpdateCouponRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@Transactional
public class CouponService {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final CouponRepository couponRepository;

    public CouponService(CouponRepository couponRepository) {
        this.couponRepository = couponRepository;
    }

    @Transactional(readOnly = true)
    public List<CouponResponse> listCoupons(boolean includeInactive) {
        List<Coupon> coupons = includeInactive
                ? couponRepository.findAllByOrderByCreatedAtDesc()
                : couponRepository.findByIsActiveTrueOrderByCreatedAtDesc();
        return coupons.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public CouponResponse getCoupon(UUID id) {
        return toResponse(findOrThrow(id));
    }

    public CouponResponse createCoupon(CreateCouponRequest request) {
        Coupon.DiscountType discountType = parseDiscountType(request.discountType());
        String code = normalise(request.code());
        OffsetDateTime validTo = endOfIstDay(request.validTo());

        validateDiscount(discountType, request.value());
        validateValidityWindow(request.validFrom(), validTo);
        if (couponRepository.existsByCode(code)) {
            throw new ConflictException("A coupon with code '" + code + "' already exists.");
        }

        Coupon coupon = new Coupon();
        coupon.setCode(code);
        applyFields(coupon, discountType, request.value(), request.minSubtotal(),
                request.validFrom(), validTo, request.usageLimit());
        coupon.setIsActive(true);

        return toResponse(couponRepository.save(coupon));
    }

    public CouponResponse updateCoupon(UUID id, UpdateCouponRequest request) {
        Coupon coupon = findOrThrow(id);
        Coupon.DiscountType discountType = parseDiscountType(request.discountType());
        OffsetDateTime validTo = endOfIstDay(request.validTo());

        validateDiscount(discountType, request.value());
        validateValidityWindow(request.validFrom(), validTo);

        applyFields(coupon, discountType, request.value(), request.minSubtotal(),
                request.validFrom(), validTo, request.usageLimit());

        return toResponse(couponRepository.save(coupon));
    }

    public CouponResponse setStatus(UUID id, SetCouponStatusRequest request) {
        Coupon coupon = findOrThrow(id);
        coupon.setIsActive(request.isActive());
        return toResponse(couponRepository.save(coupon));
    }

    private Coupon findOrThrow(UUID id) {
        return couponRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Coupon not found: " + id));
    }

    private void applyFields(
            Coupon coupon, Coupon.DiscountType discountType, int value, Integer minSubtotal,
            OffsetDateTime validFrom, OffsetDateTime validTo, Integer usageLimit
    ) {
        coupon.setDiscountType(discountType);
        coupon.setValue(value);
        coupon.setMinSubtotal(minSubtotal);
        coupon.setValidFrom(validFrom);
        coupon.setValidTo(validTo);
        // Deliberately allowed even below the current timesUsed — it just means no
        // further uses; rejecting it would prevent the owner cutting off an abused code.
        coupon.setUsageLimit(usageLimit);
    }

    private Coupon.DiscountType parseDiscountType(String raw) {
        try {
            return Coupon.DiscountType.valueOf(raw);
        } catch (IllegalArgumentException e) {
            throw new ValidationException("Discount type must be PERCENT or FIXED.");
        }
    }

    private void validateDiscount(Coupon.DiscountType discountType, int value) {
        if (discountType == Coupon.DiscountType.PERCENT && (value < 1 || value > 100)) {
            throw new ValidationException("A percentage discount must be between 1 and 100.");
        }
    }

    // Coupons are date-granularity: the owner thinks "valid through 30 Sep", not "valid
    // until some instant on 30 Sep". Whatever time a client sends, the window runs to the
    // last second of that IST day — otherwise a coupon picked as "to 30 Sep" arrives as
    // midnight and dies before the 30th trades at all. Normalised here rather than in the
    // browser so Swagger and any future non-browser client get the same semantics.
    private OffsetDateTime endOfIstDay(OffsetDateTime validTo) {
        return validTo.atZoneSameInstant(IST)
                .toLocalDate()
                .atTime(LocalTime.MAX.withNano(0))
                .atZone(IST)
                .toOffsetDateTime();
    }

    private void validateValidityWindow(OffsetDateTime validFrom, OffsetDateTime validTo) {
        if (!validTo.isAfter(validFrom)) {
            throw new ValidationException("Coupon validity end must be after its start.");
        }
    }

    private String normalise(String code) {
        return code.trim().toUpperCase(Locale.ROOT);
    }

    private CouponResponse toResponse(Coupon coupon) {
        return new CouponResponse(
                coupon.getId(),
                coupon.getCode(),
                coupon.getDiscountType().name(),
                coupon.getValue(),
                coupon.getMinSubtotal(),
                coupon.getValidFrom(),
                coupon.getValidTo(),
                coupon.getUsageLimit(),
                coupon.getTimesUsed(),
                Boolean.TRUE.equals(coupon.getIsActive()),
                coupon.getCreatedAt(),
                coupon.getUpdatedAt()
        );
    }
}
