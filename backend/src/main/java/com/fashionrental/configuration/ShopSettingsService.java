package com.fashionrental.configuration;

import com.fashionrental.configuration.model.PaymentSettingsResponse;
import com.fashionrental.configuration.model.UpdatePaymentSettingsRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ShopSettingsService {

    private final ShopSettingsRepository shopSettingsRepository;

    public ShopSettingsService(ShopSettingsRepository shopSettingsRepository) {
        this.shopSettingsRepository = shopSettingsRepository;
    }

    @Transactional(readOnly = true)
    public PaymentSettingsResponse getPaymentSettings() {
        return toResponse(load());
    }

    public PaymentSettingsResponse updatePaymentSettings(UpdatePaymentSettingsRequest request) {
        ShopSettings settings = load();
        if (request.disablesUpi()) {
            settings.setUpiVpa(null);
            settings.setUpiPayeeName(null);
        } else {
            settings.setUpiVpa(request.upiVpa());
            settings.setUpiPayeeName(request.upiPayeeName());
        }
        return toResponse(shopSettingsRepository.save(settings));
    }

    // The row is seeded by migration V20261030002, so its absence is a broken deployment,
    // not an expected state.
    private ShopSettings load() {
        return shopSettingsRepository.findById(ShopSettings.SINGLETON_ID)
                .orElseThrow(() -> new IllegalStateException("shop_settings row is missing"));
    }

    private PaymentSettingsResponse toResponse(ShopSettings settings) {
        return new PaymentSettingsResponse(
                settings.isUpiConfigured(), settings.getUpiVpa(), settings.getUpiPayeeName());
    }
}
