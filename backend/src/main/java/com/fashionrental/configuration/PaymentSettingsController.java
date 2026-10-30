package com.fashionrental.configuration;

import com.fashionrental.common.response.ApiResponse;
import com.fashionrental.configuration.model.PaymentSettingsResponse;
import com.fashionrental.configuration.model.UpdatePaymentSettingsRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Configuration", description = "Payment settings")
@RestController
@RequestMapping("/api/config/payment-settings")
public class PaymentSettingsController {

    private final ShopSettingsService shopSettingsService;

    public PaymentSettingsController(ShopSettingsService shopSettingsService) {
        this.shopSettingsService = shopSettingsService;
    }

    @Operation(summary = "Get payment settings (UPI ID and payee name)")
    @GetMapping
    public ResponseEntity<ApiResponse<PaymentSettingsResponse>> getPaymentSettings() {
        return ResponseEntity.ok(ApiResponse.ok(shopSettingsService.getPaymentSettings()));
    }

    @Operation(summary = "Update payment settings; a blank UPI ID disables UPI at checkout")
    @PutMapping
    public ResponseEntity<ApiResponse<PaymentSettingsResponse>> updatePaymentSettings(
            @Valid @RequestBody UpdatePaymentSettingsRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(shopSettingsService.updatePaymentSettings(request)));
    }
}
