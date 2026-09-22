package com.fashionrental.receipt;

import com.fashionrental.AbstractIntegrationTest;
import com.fashionrental.customer.Customer;
import com.fashionrental.customer.CustomerRepository;
import com.fashionrental.inventory.Item;
import com.fashionrental.inventory.ItemRepository;
import com.fashionrental.invoice.InvoiceRepository;
import com.fashionrental.receipt.model.request.CheckoutLineItem;
import com.fashionrental.receipt.model.request.CheckoutRequest;
import com.fashionrental.receipt.model.response.ReceiptSummaryResponse;
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
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Active Rentals screen calls listReceipts on every load, in both its default (active)
 * and overdue-filtered forms. Production runs the database in a different region from the
 * application, so every extra round trip costs ~150ms of wall clock — a per-receipt query
 * pattern is what turns this screen into a multi-second load. This pins the cost of each
 * form to the size of its result set, not the number of rows in it.
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

        createReceipt(persistCustomer("Priya", "9700011122"), item, START, END);
        long queriesForOneReceipt = countStatementsFor(this::listActiveRentals);

        createReceipt(persistCustomer("Anita", "9700011133"), item, START, END);
        createReceipt(persistCustomer("Meera", "9700011144"), item, START, END);
        createReceipt(persistCustomer("Kavya", "9700011155"), item, START, END);
        long queriesForFourReceipts = countStatementsFor(this::listActiveRentals);

        assertThat(queriesForFourReceipts)
                .as("listing 4 active rentals must not cost more queries than listing 1; "
                        + "a per-receipt customer/line-item/item lookup is an N+1")
                .isEqualTo(queriesForOneReceipt);
    }

    @Test
    void should_not_issue_more_queries_as_overdue_rentals_grow() {
        Item item = persistItem();
        OffsetDateTime overdueStart = OffsetDateTime.now().minusDays(3);
        OffsetDateTime overdueEnd = OffsetDateTime.now().minusDays(1);

        createReceipt(persistCustomer("Divya", "9700011166"), item, overdueStart, overdueEnd);
        long queriesForOneReceipt = countStatementsFor(this::listOverdueRentals);

        createReceipt(persistCustomer("Farah", "9700011177"), item, overdueStart, overdueEnd);
        createReceipt(persistCustomer("Ishita", "9700011188"), item, overdueStart, overdueEnd);
        long queriesForThreeReceipts = countStatementsFor(this::listOverdueRentals);

        assertThat(queriesForThreeReceipts)
                .as("listing 3 overdue rentals must not cost more queries than listing 1; "
                        + "the overdue finder needs the same entity graph as the active-rentals one")
                .isEqualTo(queriesForOneReceipt);
    }

    private List<ReceiptSummaryResponse> listActiveRentals() {
        return receiptService.listReceipts(Receipt.Status.GIVEN, null);
    }

    private List<ReceiptSummaryResponse> listOverdueRentals() {
        return receiptService.listReceipts(null, true);
    }

    private long countStatementsFor(Supplier<List<ReceiptSummaryResponse>> listingCall) {
        statistics.clear();
        List<ReceiptSummaryResponse> listed = listingCall.get();
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

    private void createReceipt(Customer customer, Item item, OffsetDateTime start, OffsetDateTime end) {
        checkoutService.createReceipt(new CheckoutRequest(
                customer.getId(), start, end,
                List.of(new CheckoutLineItem(item.getId(), 1)),
                List.of(),
                null,
                null
        ));
    }
}
