package com.fashionrental.receipt;

import com.fashionrental.common.response.ApiResponse;
import com.fashionrental.configuration.ShopSettingsService;
import com.fashionrental.configuration.model.PaymentSettingsResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

// Deliberately outside /api/config/** (OWNER-only): every staff role checking out needs to read
// the UPI details to show the QR, while only the owner may change them.
@Tag(name = "Receipts", description = "Checkout and receipt management")
@RestController
public class CheckoutPaymentOptionsController {

    private final ShopSettingsService shopSettingsService;

    public CheckoutPaymentOptionsController(ShopSettingsService shopSettingsService) {
        this.shopSettingsService = shopSettingsService;
    }

    @Operation(summary = "Payment methods available at checkout (any authenticated staff role)")
    @GetMapping("/api/checkout/payment-options")
    public ResponseEntity<ApiResponse<PaymentSettingsResponse>> getPaymentOptions() {
        return ResponseEntity.ok(ApiResponse.ok(shopSettingsService.getPaymentSettings()));
    }
}
