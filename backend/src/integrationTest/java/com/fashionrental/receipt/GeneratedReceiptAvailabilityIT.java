package com.fashionrental.receipt;

import com.fashionrental.AbstractIntegrationTest;
import com.fashionrental.common.exception.ConflictException;
import com.fashionrental.customer.Customer;
import com.fashionrental.customer.CustomerRepository;
import com.fashionrental.inventory.Item;
import com.fashionrental.inventory.ItemRepository;
import com.fashionrental.receipt.model.request.CancelReceiptRequest;
import com.fashionrental.receipt.model.request.CheckoutLineItem;
import com.fashionrental.receipt.model.request.CheckoutRequest;
import com.fashionrental.receipt.model.response.ReceiptResponse;
import org.junit.jupiter.api.AfterEach;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A booked-but-not-yet-handed-over (GENERATED) receipt must hold its dress, otherwise the same
 * dress could be double-booked for the same slot.
 */
@Transactional
class GeneratedReceiptAvailabilityIT extends AbstractIntegrationTest {

    private static final OffsetDateTime START = OffsetDateTime.parse("2026-12-20T10:00:00+05:30");
    private static final OffsetDateTime END = OffsetDateTime.parse("2026-12-20T11:00:00+05:30");

    @Autowired private CheckoutService checkoutService;
    @Autowired private ReceiptGivenService receiptGivenService;
    @Autowired private ReceiptCancellationService receiptCancellationService;
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
        customer.setPhone("9811122244");
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

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void should_block_a_second_booking_for_the_same_slot_while_the_first_is_generated() {
        ReceiptResponse first = book();
        assertThat(first.status()).isEqualTo("GENERATED");

        assertThatThrownBy(this::book).isInstanceOf(ConflictException.class);
    }

    @Test
    void should_keep_blocking_the_slot_after_the_dress_is_given() {
        ReceiptResponse first = book();
        receiptGivenService.markGiven(first.id());

        assertThatThrownBy(this::book).isInstanceOf(ConflictException.class);
    }

    @Test
    void should_free_the_slot_when_the_generated_receipt_is_cancelled() {
        ReceiptResponse first = book();
        receiptCancellationService.cancelReceipt(first.id(),
                new CancelReceiptRequest(
                        Receipt.CancellationReason.CUSTOMER_DOES_NOT_WANT, null),
                ownerUsername);

        assertThat(book().status()).isEqualTo("GENERATED");
    }

    private ReceiptResponse book() {
        return checkoutService.createReceipt(new CheckoutRequest(
                customer.getId(), START, END,
                List.of(new CheckoutLineItem(singleUnitItem.getId(), 1)),
                List.of(), null, null));
    }
}
