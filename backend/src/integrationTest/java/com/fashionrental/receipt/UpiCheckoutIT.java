package com.fashionrental.receipt;

import com.fashionrental.AbstractIntegrationTest;
import com.fashionrental.configuration.ShopSettingsService;
import com.fashionrental.customer.Customer;
import com.fashionrental.customer.CustomerRepository;
import com.fashionrental.inventory.Item;
import com.fashionrental.inventory.ItemRepository;
import com.fashionrental.receipt.model.request.CheckoutLineItem;
import com.fashionrental.receipt.model.request.CheckoutRequest;
import com.fashionrental.receipt.model.response.ReceiptResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
class UpiCheckoutIT extends AbstractIntegrationTest {

    private static final OffsetDateTime START = OffsetDateTime.parse("2026-12-21T10:00:00+05:30");
    private static final OffsetDateTime END = OffsetDateTime.parse("2026-12-21T11:00:00+05:30");
    // rent 300 * 1 day + deposit 1000
    private static final int GRAND_TOTAL = 1300;

    @Autowired private CheckoutService checkoutService;
    @Autowired private ShopSettingsService shopSettingsService;
    @Autowired private ReceiptRepository receiptRepository;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private ItemRepository itemRepository;

    @Value("${app.auth.username}")
    private String ownerUsername;

    private Customer customer;
    private Item singleUnitItem;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                ownerUsername, null, List.of(new SimpleGrantedAuthority("ROLE_OWNER"))));

        customer = new Customer();
        customer.setName("Meera");
        customer.setPhone("9811122255");
        customer.setCustomerType(Customer.CustomerType.MISC);
        customer.setIsActive(true);
        customer = customerRepository.save(customer);

        singleUnitItem = new Item();
        singleUnitItem.setName("Bridal Lehenga");
        singleUnitItem.setCategory(Item.Category.COSTUME);
        singleUnitItem.setItemType(Item.ItemType.INDIVIDUAL);
        singleUnitItem.setRate(300);
        singleUnitItem.setDeposit(1000);
        singleUnitItem.setQuantity(1);
        singleUnitItem.setIsActive(true);
        singleUnitItem = itemRepository.save(singleUnitItem);
    }

    private CheckoutRequest upiRequest(UUID idempotencyKey) {
        return new CheckoutRequest(
                customer.getId(), START, END,
                List.of(new CheckoutLineItem(singleUnitItem.getId(), 1)),
                List.of(), null, null,
                ReceiptPaymentMethod.UPI, GRAND_TOTAL, idempotencyKey);
    }

    @Test
    void should_start_with_upi_disabled_from_the_seeded_settings_row() {
        assertThat(shopSettingsService.getPaymentSettings().upiEnabled()).isFalse();
    }

    @Test
    void should_return_the_same_receipt_when_a_upi_confirmation_is_retried() {
        UUID key = UUID.randomUUID();

        ReceiptResponse first = checkoutService.createReceipt(upiRequest(key));
        // The only unit is now booked, so a second, non-replayed checkout would fail availability.
        ReceiptResponse retried = checkoutService.createReceipt(upiRequest(key));

        assertThat(retried.id()).isEqualTo(first.id());
        assertThat(first.paymentMethod()).isEqualTo("UPI");
        assertThat(receiptRepository.findByIdempotencyKey(key)).isPresent();
    }
}
