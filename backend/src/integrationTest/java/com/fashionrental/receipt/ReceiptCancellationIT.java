package com.fashionrental.receipt;

import com.fashionrental.AbstractIntegrationTest;
import com.fashionrental.common.exception.ConflictException;
import com.fashionrental.customer.Customer;
import com.fashionrental.customer.CustomerRepository;
import com.fashionrental.inventory.AvailabilityService;
import com.fashionrental.inventory.Item;
import com.fashionrental.inventory.ItemRepository;
import com.fashionrental.inventory.PackageComponent;
import com.fashionrental.inventory.PackageComponentRepository;
import com.fashionrental.invoice.InvoiceRepository;
import com.fashionrental.invoice.ReturnService;
import com.fashionrental.invoice.model.request.ProcessReturnRequest;
import com.fashionrental.invoice.model.request.ReturnLineItem;
import com.fashionrental.receipt.model.request.CancelReceiptRequest;
import com.fashionrental.receipt.model.request.CheckoutLineItem;
import com.fashionrental.receipt.model.request.CheckoutRequest;
import com.fashionrental.receipt.model.response.ReceiptResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

/**
 * Exercises receipt cancellation (#166) against real PostgreSQL: the migration's columns and
 * CHECK constraints, inventory release through the real availability queries, and the
 * cancel-vs-return row lock — none of which a mocked unit test can observe.
 *
 * <p>Deliberately NOT @Transactional: the concurrency test needs two real transactions on
 * separate connections, and the constraint tests need statements to actually reach the DB.
 */
class ReceiptCancellationIT extends AbstractIntegrationTest {

    private static final Duration LOCK_WAIT_TIMEOUT = Duration.ofSeconds(10);

    @Autowired private CheckoutService checkoutService;
    @Autowired private ReceiptCancellationService receiptCancellationService;
    @Autowired private ReturnService returnService;
    @Autowired private AvailabilityService availabilityService;
    @Autowired private ReceiptRepository receiptRepository;
    @Autowired private InvoiceRepository invoiceRepository;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private ItemRepository itemRepository;
    @Autowired private PackageComponentRepository packageComponentRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    @MockitoSpyBean private ReceiptMapper receiptMapper;

    @Value("${app.auth.username}")
    private String ownerUsername;

    // Relative to now: cancellation is only allowed more than 12 hours before the end.
    private final OffsetDateTime start = OffsetDateTime.now().plusDays(1).truncatedTo(ChronoUnit.SECONDS);
    private final OffsetDateTime end = start.plusDays(2);

    @AfterEach
    void cleanUp() {
        invoiceRepository.deleteAll();
        receiptRepository.deleteAll();
        packageComponentRepository.deleteAll();
        itemRepository.deleteAll();
        customerRepository.deleteAll();
    }

    @Test
    void cancel_persists_status_and_full_audit_trail() {
        Item sherwani = itemRepository.save(newIndividualItem("Royal Sherwani", 1));
        ReceiptResponse receipt = checkout(newCustomer("Meera", "9811100101"), sherwani);

        receiptCancellationService.cancelReceipt(receipt.id(),
                new CancelReceiptRequest(Receipt.CancellationReason.OTHER, "  customer moved cities  "),
                ownerUsername);

        Map<String, Object> row = jdbcTemplate.queryForMap("""
                SELECT r.status, r.cancelled_at, u.username, r.cancellation_reason, r.cancellation_reason_detail
                FROM receipts r JOIN users u ON u.id = r.cancelled_by_user_id
                WHERE r.id = ?""", receipt.id());
        assertThat(row.get("status")).isEqualTo("CANCELLED");
        assertThat(row.get("cancelled_at")).isNotNull();
        assertThat(row.get("username")).isEqualTo(ownerUsername);
        assertThat(row.get("cancellation_reason")).isEqualTo("OTHER");
        assertThat(row.get("cancellation_reason_detail")).isEqualTo("customer moved cities");
    }

    @Test
    void cancelling_a_package_receipt_releases_the_package_and_its_components() {
        Item turban = itemRepository.save(newIndividualItem("Turban", 2));
        Item weddingPackage = newIndividualItem("Wedding Package", 1);
        weddingPackage.setItemType(Item.ItemType.PACKAGE);
        weddingPackage = itemRepository.save(weddingPackage);
        PackageComponent component = new PackageComponent();
        component.setPackageItem(weddingPackage);
        component.setComponentItem(turban);
        component.setQuantity(2);
        packageComponentRepository.save(component);

        ReceiptResponse receipt = checkout(newCustomer("Kavita", "9811100102"), weddingPackage);
        assertThat(availabilityService.getAvailableQuantity(weddingPackage.getId(), start, end)).isZero();
        assertThat(availabilityService.getAvailableQuantity(turban.getId(), start, end)).isZero();

        cancel(receipt.id());

        assertThat(availabilityService.getAvailableQuantity(weddingPackage.getId(), start, end)).isEqualTo(1);
        assertThat(availabilityService.getAvailableQuantity(turban.getId(), start, end)).isEqualTo(2);
    }

    @Test
    void cancelling_one_receipt_leaves_another_booking_of_the_same_item_in_place() {
        Item gown = itemRepository.save(newIndividualItem("Party Gown", 2));
        ReceiptResponse cancelled = checkout(newCustomer("Rekha", "9811100103"), gown);
        checkout(newCustomer("Suresh", "9811100104"), gown);

        cancel(cancelled.id());

        assertThat(availabilityService.getAvailableQuantity(gown.getId(), start, end)).isEqualTo(1);
    }

    @Test
    void database_rejects_a_cancelled_status_without_the_audit_trail() {
        Item sherwani = itemRepository.save(newIndividualItem("Royal Sherwani", 1));
        ReceiptResponse receipt = checkout(newCustomer("Anil", "9811100105"), sherwani);

        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE receipts SET status = 'CANCELLED' WHERE id = ?", receipt.id()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("receipts_cancellation_audit_check");
    }

    @Test
    void database_rejects_reason_other_without_a_description() {
        Item sherwani = itemRepository.save(newIndividualItem("Royal Sherwani", 1));
        ReceiptResponse receipt = checkout(newCustomer("Priya", "9811100106"), sherwani);

        assertThatThrownBy(() -> jdbcTemplate.update("""
                UPDATE receipts
                SET status = 'CANCELLED', cancelled_at = now(),
                    cancelled_by_user_id = (SELECT id FROM users WHERE username = ?),
                    cancellation_reason = 'OTHER', cancellation_reason_detail = '   '
                WHERE id = ?""", ownerUsername, receipt.id()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("receipts_cancellation_detail_check");
    }

    @Test
    void a_return_racing_a_cancel_waits_for_the_row_lock_and_then_is_rejected() throws Exception {
        Item sherwani = itemRepository.save(newIndividualItem("Royal Sherwani", 1));
        ReceiptResponse receipt = checkout(newCustomer("Deepa", "9811100107"), sherwani);
        UUID lineItemId = receipt.lineItems().get(0).id();

        // The cancel transaction pauses after taking the row lock (the mapper runs before commit)
        // and only proceeds once Postgres reports the return's transaction waiting on a lock. With a
        // plain read instead of findByIdForUpdate, the return never waits and this times out.
        CountDownLatch cancelHoldsLock = new CountDownLatch(1);
        doAnswer(invocation -> {
            cancelHoldsLock.countDown();
            awaitAnotherTransactionBlockedOnALock();
            return invocation.callRealMethod();
        }).when(receiptMapper).toReceiptResponse(any());

        ExecutorService pool = Executors.newFixedThreadPool(2);
        Future<?> cancelling = pool.submit(() -> cancel(receipt.id()));
        Future<Exception> returning = pool.submit(() -> {
            cancelHoldsLock.await(LOCK_WAIT_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
            try {
                returnService.processReturn(receipt.id(), new ProcessReturnRequest(
                        end, List.of(new ReturnLineItem(lineItemId, false, null, null)), "CASH", null, null));
                return null;
            } catch (Exception e) {
                return e;
            }
        });

        cancelling.get(15, TimeUnit.SECONDS);
        Exception returnFailure = returning.get(15, TimeUnit.SECONDS);
        pool.shutdown();

        assertThat(returnFailure)
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("has been cancelled and cannot be returned");
        assertThat(receiptRepository.findById(receipt.id()).orElseThrow().getStatus())
                .isEqualTo(Receipt.Status.CANCELLED);
        assertThat(invoiceRepository.count()).isZero();
    }

    private void awaitAnotherTransactionBlockedOnALock() throws InterruptedException {
        long deadline = System.nanoTime() + LOCK_WAIT_TIMEOUT.toNanos();
        while (System.nanoTime() < deadline) {
            Integer waiting = jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM pg_locks WHERE NOT granted", Integer.class);
            if (waiting != null && waiting > 0) {
                return;
            }
            Thread.sleep(25);
        }
        throw new IllegalStateException("No transaction ever waited on the receipt row lock");
    }

    private ReceiptResponse checkout(Customer customer, Item item) {
        return checkoutService.createReceipt(new CheckoutRequest(
                customer.getId(), start, end,
                List.of(new CheckoutLineItem(item.getId(), 1)),
                List.of(), null, null));
    }

    private void cancel(UUID receiptId) {
        receiptCancellationService.cancelReceipt(receiptId,
                new CancelReceiptRequest(Receipt.CancellationReason.WRONG_ORDER, null), ownerUsername);
    }

    private Customer newCustomer(String name, String phone) {
        Customer customer = new Customer();
        customer.setName(name);
        customer.setPhone(phone);
        customer.setCustomerType(Customer.CustomerType.MISC);
        customer.setIsActive(true);
        return customerRepository.save(customer);
    }

    private Item newIndividualItem(String name, int quantity) {
        Item item = new Item();
        item.setName(name);
        item.setCategory(Item.Category.COSTUME);
        item.setItemType(Item.ItemType.INDIVIDUAL);
        item.setRate(300);
        item.setDeposit(1000);
        item.setQuantity(quantity);
        item.setIsActive(true);
        return item;
    }
}
