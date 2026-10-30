package com.fashionrental.configuration;

import com.fashionrental.config.SecurityConfig;
import com.fashionrental.config.SecurityErrorHandler;
import com.fashionrental.configuration.model.PaymentSettingsResponse;
import com.fashionrental.receipt.CheckoutPaymentOptionsController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Real SecurityConfig chain: the split between the OWNER-only settings endpoint and the
// any-staff checkout read is enforced purely by URL placement, so it needs a regression guard.
@WebMvcTest({PaymentSettingsController.class, CheckoutPaymentOptionsController.class})
@Import({SecurityConfig.class, SecurityErrorHandler.class})
class PaymentSettingsControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private ShopSettingsService shopSettingsService;
    @MockitoBean private com.fashionrental.config.JwtConfig jwtConfig;

    private static final PaymentSettingsResponse CONFIGURED =
            new PaymentSettingsResponse(true, "shop@ybl", "Shop");

    @Test
    @WithMockUser(roles = "OWNER")
    void owner_can_update_payment_settings() throws Exception {
        when(shopSettingsService.updatePaymentSettings(any())).thenReturn(CONFIGURED);

        mockMvc.perform(put("/api/config/payment-settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"upiVpa\":\"shop@ybl\",\"upiPayeeName\":\"Shop\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.upiVpa").value("shop@ybl"));
    }

    @Test
    @WithMockUser(roles = "EXECUTIVE")
    void executive_cannot_update_payment_settings() throws Exception {
        mockMvc.perform(put("/api/config/payment-settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"upiVpa\":\"attacker@ybl\",\"upiPayeeName\":\"X\"}"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(shopSettingsService);
    }

    @Test
    @WithMockUser(roles = "EXECUTIVE")
    void executive_can_read_checkout_payment_options() throws Exception {
        when(shopSettingsService.getPaymentSettings()).thenReturn(CONFIGURED);

        mockMvc.perform(get("/api/checkout/payment-options"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.upiEnabled").value(true))
                .andExpect(jsonPath("$.data.upiPayeeName").value("Shop"));
    }

    @Test
    void anonymous_cannot_read_checkout_payment_options() throws Exception {
        mockMvc.perform(get("/api/checkout/payment-options"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "OWNER")
    void should_reject_malformed_upi_id() throws Exception {
        mockMvc.perform(put("/api/config/payment-settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"upiVpa\":\"not a vpa\",\"upiPayeeName\":\"Shop\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(shopSettingsService);
    }

    @Test
    @WithMockUser(roles = "OWNER")
    void should_reject_upi_id_without_payee_name() throws Exception {
        mockMvc.perform(put("/api/config/payment-settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"upiVpa\":\"shop@ybl\",\"upiPayeeName\":\" \"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(shopSettingsService);
    }
}
