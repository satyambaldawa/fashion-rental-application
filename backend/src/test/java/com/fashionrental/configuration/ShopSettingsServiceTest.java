package com.fashionrental.configuration;

import com.fashionrental.configuration.model.PaymentSettingsResponse;
import com.fashionrental.configuration.model.UpdatePaymentSettingsRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShopSettingsServiceTest {

    @Mock private ShopSettingsRepository shopSettingsRepository;
    @InjectMocks private ShopSettingsService shopSettingsService;

    private ShopSettings settings;

    @BeforeEach
    void setUp() {
        settings = new ShopSettings();
        settings.setId(ShopSettings.SINGLETON_ID);
        when(shopSettingsRepository.findById(ShopSettings.SINGLETON_ID)).thenReturn(Optional.of(settings));
    }

    @Test
    void should_report_upi_disabled_when_no_vpa_is_set() {
        PaymentSettingsResponse response = shopSettingsService.getPaymentSettings();

        assertThat(response.upiEnabled()).isFalse();
    }

    @Test
    void should_store_trimmed_vpa_and_payee_name() {
        when(shopSettingsRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PaymentSettingsResponse response = shopSettingsService.updatePaymentSettings(
                new UpdatePaymentSettingsRequest(" shop@ybl ", " Manisha Collection "));

        assertThat(response).isEqualTo(new PaymentSettingsResponse(true, "shop@ybl", "Manisha Collection"));
    }

    @Test
    void should_disable_upi_and_clear_payee_name_when_vpa_is_blank() {
        settings.setUpiVpa("shop@ybl");
        settings.setUpiPayeeName("Shop");
        when(shopSettingsRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PaymentSettingsResponse response = shopSettingsService.updatePaymentSettings(
                new UpdatePaymentSettingsRequest("  ", "Shop"));

        assertThat(response).isEqualTo(new PaymentSettingsResponse(false, null, null));
    }
}
