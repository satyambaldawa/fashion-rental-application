package com.fashionrental.configuration;

import com.fashionrental.common.response.ApiResponse;
import com.fashionrental.configuration.model.CouponResponse;
import com.fashionrental.configuration.model.CreateCouponRequest;
import com.fashionrental.configuration.model.SetCouponStatusRequest;
import com.fashionrental.configuration.model.UpdateCouponRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Configuration", description = "Coupon configuration")
@RestController
@RequestMapping("/api/config/coupons")
public class CouponController {

    private final CouponService couponService;

    public CouponController(CouponService couponService) {
        this.couponService = couponService;
    }

    @Operation(summary = "List coupons")
    @GetMapping
    public ResponseEntity<ApiResponse<List<CouponResponse>>> listCoupons(
            @RequestParam(defaultValue = "false") boolean includeInactive) {
        return ResponseEntity.ok(ApiResponse.ok(couponService.listCoupons(includeInactive)));
    }

    @Operation(summary = "Get a coupon by id")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<CouponResponse>> getCoupon(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(couponService.getCoupon(id)));
    }

    @Operation(summary = "Create a coupon")
    @PostMapping
    public ResponseEntity<ApiResponse<CouponResponse>> createCoupon(
            @Valid @RequestBody CreateCouponRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(couponService.createCoupon(request)));
    }

    @Operation(summary = "Update a coupon's terms")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<CouponResponse>> updateCoupon(
            @PathVariable UUID id, @Valid @RequestBody UpdateCouponRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(couponService.updateCoupon(id, request)));
    }

    @Operation(summary = "Activate or deactivate a coupon")
    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<CouponResponse>> setStatus(
            @PathVariable UUID id, @Valid @RequestBody SetCouponStatusRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(couponService.setStatus(id, request)));
    }
}
