package com.fashionrental.receipt;

import com.fashionrental.AbstractIntegrationTest;
import com.fashionrental.customer.Customer;
import com.fashionrental.customer.CustomerRepository;
import com.fashionrental.inventory.Item;
import com.fashionrental.inventory.ItemRepository;
import com.fashionrental.invoice.InvoiceRepository;
import com.fashionrental.receipt.model.request.CheckoutLineItem;
import com.fashionrental.receipt.model.request.CheckoutRequest;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
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

/**
 * The Active Rentals screen calls listReceipts on every load. Production runs the database
 * in a different region from the application, so every extra round trip costs ~150ms of
 * wall clock — a per-receipt query pattern is what turns this screen into a multi-second
 * load. This pins the cost to the size of the result set, not the number of rows in it.
 *
 * <p>Deliberately NOT @Transactional: a test-managed transaction would keep one persistence
 * context open across both measurements, so lazy associations loaded while building the
 * fixtures would already be cached and the second measurement would read zero.
 */
class ActiveRentalsQueryCountIT extends AbstractIntegrationTest {

    private static final OffsetDateTime START = OffsetDateTime.parse("2026-04-18T10:00:00+05:30");
    private static final OffsetDateTime END = OffsetDateTime.parse("2026-04-21T10:00:00+05:30");

    @Autowired private CheckoutService checkoutService;
    @Autowired private ReceiptService receiptService;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private ItemRepository itemRepository;
    @Autowired private ReceiptRepository receiptRepository;
    @Autowired private InvoiceRepository invoiceRepository;
    @Autowired private EntityManagerFactory entityManagerFactory;

    private Statistics statistics;

    @BeforeEach
    void setUp() {
        var auth = new UsernamePasswordAuthenticationToken(
                "owner", null, List.of(new SimpleGrantedAuthority("ROLE_OWNER")));
        SecurityContextHolder.getContext().setAuthentication(auth);

        statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
    }

    @AfterEach
    void cleanUp() {
        SecurityContextHolder.clearContext();
        statistics.setStatisticsEnabled(false);
        invoiceRepository.deleteAll();
        receiptRepository.deleteAll();
        itemRepository.deleteAll();
        customerRepository.deleteAll();
    }

    @Test
    void should_not_issue_more_queries_as_active_rentals_grow() {
        Item item = persistItem();

        createReceipt(persistCustomer("Priya", "9700011122"), item);
        long queriesForOneReceipt = countStatementsListingActiveRentals();

        createReceipt(persistCustomer("Anita", "9700011133"), item);
        createReceipt(persistCustomer("Meera", "9700011144"), item);
        createReceipt(persistCustomer("Kavya", "9700011155"), item);
        long queriesForFourReceipts = countStatementsListingActiveRentals();

        assertThat(queriesForFourReceipts)
                .as("listing 4 active rentals must not cost more queries than listing 1; "
                        + "a per-receipt customer/line-item/item lookup is an N+1")
                .isEqualTo(queriesForOneReceipt);
    }

    private long countStatementsListingActiveRentals() {
        statistics.clear();
        List<?> listed = receiptService.listReceipts(Receipt.Status.GIVEN, null);
        assertThat(listed).isNotEmpty();
        return statistics.getPrepareStatementCount();
    }

    private Customer persistCustomer(String name, String phone) {
        Customer customer = new Customer();
        customer.setName(name);
        customer.setPhone(phone);
        customer.setCustomerType(Customer.CustomerType.MISC);
        customer.setIsActive(true);
        return customerRepository.save(customer);
    }

    private Item persistItem() {
        Item item = new Item();
        item.setName("Wedding Sherwani");
        item.setCategory(Item.Category.COSTUME);
        item.setItemType(Item.ItemType.INDIVIDUAL);
        item.setRate(300);
        item.setDeposit(1000);
        item.setQuantity(10);
        item.setIsActive(true);
        return itemRepository.save(item);
    }

    private void createReceipt(Customer customer, Item item) {
        checkoutService.createReceipt(new CheckoutRequest(
                customer.getId(), START, END,
                List.of(new CheckoutLineItem(item.getId(), 1)),
                List.of(),
                null,
                null
        ));
    }
}
