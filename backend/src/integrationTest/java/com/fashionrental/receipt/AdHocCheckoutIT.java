package com.fashionrental.receipt;

import com.fashionrental.AbstractIntegrationTest;
import com.fashionrental.common.exception.ConflictException;
import com.fashionrental.customer.Customer;
import com.fashionrental.customer.CustomerRepository;
import com.fashionrental.inventory.Item;
import com.fashionrental.inventory.ItemRepository;
import com.fashionrental.invoice.InvoiceRepository;
import com.fashionrental.receipt.model.request.AdHocLineItem;
import com.fashionrental.receipt.model.request.CheckoutLineItem;
import com.fashionrental.receipt.model.request.CheckoutRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Deliberately NOT @Transactional: this asserts that CheckoutService's own transaction
 * rolls back. A test-managed transaction would make the ad-hoc rows invisible to the
 * assertion regardless of whether the rollback actually happened.
 *
 * <p>Note: in this request, the catalogue-line conflict is detected before the ad-hoc loop
 * ever runs (createReceipt processes request.items() first), so no ad-hoc INSERT is even
 * attempted here — the assertion holds by validation order in this specific scenario. It
 * would also hold by @Transactional rollback if the loop order were ever reversed; nothing
 * in this test distinguishes the two, but the outcome the issue's acceptance criterion asks
 * for (zero orphan rows after a conflict) is what's verified either way.
 */
class AdHocCheckoutIT extends AbstractIntegrationTest {

    @Autowired private CheckoutService checkoutService;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private ItemRepository itemRepository;
    @Autowired private ReceiptRepository receiptRepository;
    @Autowired private InvoiceRepository invoiceRepository;

    @BeforeEach
    void authenticateAsOwner() {
        var auth = new UsernamePasswordAuthenticationToken(
                "owner", null, List.of(new SimpleGrantedAuthority("ROLE_OWNER")));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    // Deletion order matters: Invoice FKs to Receipt (no cascade), Receipt cascades to its own
    // ReceiptLineItems but Item has no knowledge of either. A future happy-path test in this class
    // that persists a receipt (and, via return, an invoice) would otherwise leave an FK violation
    // here rather than in the test itself.
    @AfterEach
    void cleanUp() {
        SecurityContextHolder.clearContext();
        invoiceRepository.deleteAll();
        receiptRepository.deleteAll();
        itemRepository.deleteAll();
        customerRepository.deleteAll();
    }

    @Test
    void should_not_persist_ad_hoc_items_when_a_catalogue_line_conflicts() {
        Customer customer = new Customer();
        customer.setName("Priya");
        customer.setPhone("9700011122");
        customer.setCustomerType(Customer.CustomerType.MISC);
        customer.setIsActive(true);
        customer = customerRepository.save(customer);

        Item soldOut = new Item();
        soldOut.setName("Sold Out Sherwani");
        soldOut.setCategory(Item.Category.COSTUME);
        soldOut.setItemType(Item.ItemType.INDIVIDUAL);
        soldOut.setRate(300);
        soldOut.setDeposit(1000);
        soldOut.setQuantity(0);          // nothing available → ConflictException
        soldOut.setIsActive(true);
        soldOut = itemRepository.save(soldOut);

        long itemsBefore = itemRepository.count();

        OffsetDateTime start = OffsetDateTime.parse("2026-04-18T10:00:00+05:30");
        OffsetDateTime end = OffsetDateTime.parse("2026-04-21T10:00:00+05:30");

        Customer finalCustomer = customer;
        Item finalSoldOut = soldOut;
        assertThatThrownBy(() -> checkoutService.createReceipt(new CheckoutRequest(
                finalCustomer.getId(), start, end,
                List.of(new CheckoutLineItem(finalSoldOut.getId(), 1)),
                List.of(new AdHocLineItem("Orphan Lehenga", "M", 500, 1000, 1)),
                null,
                null
        ))).isInstanceOf(ConflictException.class);

        assertThat(itemRepository.count())
                .as("the ad-hoc item must not have been persisted alongside the failed receipt")
                .isEqualTo(itemsBefore);
    }
}
