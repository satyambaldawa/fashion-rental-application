package com.fashionrental.receipt;

import com.fashionrental.AbstractIntegrationTest;
import com.fashionrental.common.exception.ConflictException;
import com.fashionrental.customer.Customer;
import com.fashionrental.customer.CustomerRepository;
import com.fashionrental.inventory.AvailabilityService;
import com.fashionrental.inventory.Item;
import com.fashionrental.inventory.ItemRepository;
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
 * Exercises undoing a mistaken "Mark as Given" (#179) against real PostgreSQL: the migration's
 * columns and CHECK constraint, and the row lock that serialises undo against a concurrent
 * cancel or return — none of which a mocked unit test can observe.
 *
 * <p>Deliberately NOT @Transactional, same reasoning as ReceiptCancellationIT: the concurrency
 * tests need two real transactions on separate connections.
 */
class ReceiptGivenUndoIT extends AbstractIntegrationTest {

    private static final Duration LOCK_WAIT_TIMEOUT = Duration.ofSeconds(10);

    @Autowired private CheckoutService checkoutService;
    @Autowired private ReceiptGivenService receiptGivenService;
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
    void undo_persists_generated_status_cleared_given_at_and_revert_audit() {
        Item sherwani = itemRepository.save(newIndividualItem("Royal Sherwani", 1));
        ReceiptResponse receipt = checkout(newCustomer("Meera", "9811100201"), sherwani);
        receiptGivenService.markGiven(receipt.id());

        receiptGivenService.undoGiven(receipt.id(), ownerUsername);

        Map<String, Object> row = jdbcTemplate.queryForMap("""
                SELECT r.status, r.given_at, r.given_reverted_at, u.username AS reverted_by
                FROM receipts r JOIN users u ON u.id = r.given_reverted_by_user_id
                WHERE r.id = ?""", receipt.id());
        assertThat(row.get("status")).isEqualTo("GENERATED");
        assertThat(row.get("given_at")).isNull();
        assertThat(row.get("given_reverted_at")).isNotNull();
        assertThat(row.get("reverted_by")).isEqualTo(ownerUsername);
    }

    @Test
    void an_undo_racing_a_cancel_waits_for_the_row_lock_and_cannot_resurrect_the_receipt() throws Exception {
        Item sherwani = itemRepository.save(newIndividualItem("Royal Sherwani", 1));
        ReceiptResponse receipt = checkout(newCustomer("Deepa", "9811100202"), sherwani);

        Exception undoFailure = runWhileHoldingRowLock(
                () -> cancel(receipt.id()),
                () -> receiptGivenService.undoGiven(receipt.id(), ownerUsername));

        assertThat(undoFailure)
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("is CANCELLED and its hand-over can no longer be undone");
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT status, given_at, given_reverted_at FROM receipts WHERE id = ?", receipt.id());
        assertThat(row.get("status")).isEqualTo("CANCELLED");
        assertThat(row.get("given_at")).isNull();
        assertThat(row.get("given_reverted_at")).isNull();
    }

    @Test
    void a_return_racing_an_undo_waits_for_the_row_lock_and_is_rejected() throws Exception {
        Item sherwani = itemRepository.save(newIndividualItem("Royal Sherwani", 1));
        ReceiptResponse receipt = checkout(newCustomer("Neha", "9811100203"), sherwani);
        receiptGivenService.markGiven(receipt.id());
        UUID lineItemId = receipt.lineItems().get(0).id();

        Exception returnFailure = runWhileHoldingRowLock(
                () -> receiptGivenService.undoGiven(receipt.id(), ownerUsername),
                () -> returnService.processReturn(receipt.id(), new ProcessReturnRequest(
                        end, List.of(new ReturnLineItem(lineItemId, false, null, null)), "CASH", null, null)));

        assertThat(returnFailure)
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("has not been given to the customer yet");
        assertThat(receiptRepository.findById(receipt.id()).orElseThrow().getStatus())
                .isEqualTo(Receipt.Status.GENERATED);
        assertThat(invoiceRepository.count()).isZero();
    }

    @Test
    void give_and_undo_leave_availability_unchanged() {
        Item sherwani = itemRepository.save(newIndividualItem("Royal Sherwani", 1));
        ReceiptResponse receipt = checkout(newCustomer("Ravi", "9811100204"), sherwani);
        assertThat(availabilityService.getAvailableQuantity(sherwani.getId(), start, end)).isZero();

        receiptGivenService.markGiven(receipt.id());
        assertThat(availabilityService.getAvailableQuantity(sherwani.getId(), start, end)).isZero();

        receiptGivenService.undoGiven(receipt.id(), ownerUsername);
        assertThat(availabilityService.getAvailableQuantity(sherwani.getId(), start, end)).isZero();
    }

    @Test
    void database_rejects_a_half_written_given_revert_audit() {
        Item sherwani = itemRepository.save(newIndividualItem("Royal Sherwani", 1));
        ReceiptResponse receipt = checkout(newCustomer("Sita", "9811100205"), sherwani);

        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE receipts SET given_reverted_at = now() WHERE id = ?", receipt.id()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("receipts_given_reverted_audit_check");
    }

    @Test
    void undo_then_give_sets_a_fresh_given_at() {
        Item sherwani = itemRepository.save(newIndividualItem("Royal Sherwani", 1));
        ReceiptResponse receipt = checkout(newCustomer("Anil", "9811100206"), sherwani);
        receiptGivenService.markGiven(receipt.id());
        OffsetDateTime firstGivenAt = jdbcTemplate.queryForObject(
                "SELECT given_at FROM receipts WHERE id = ?", OffsetDateTime.class, receipt.id());

        receiptGivenService.undoGiven(receipt.id(), ownerUsername);
        receiptGivenService.markGiven(receipt.id());

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT status, given_at, given_reverted_at FROM receipts WHERE id = ?", receipt.id());
        assertThat(row.get("status")).isEqualTo("GIVEN");
        assertThat(row.get("given_at")).isNotNull();
        assertThat((OffsetDateTime) row.get("given_at")).isAfter(firstGivenAt);
        assertThat(row.get("given_reverted_at")).isNotNull();
    }

    /**
     * The holder transaction pauses after taking the row lock (the mapper runs before commit) and
     * only proceeds once Postgres reports the contender's transaction waiting on a lock. With a
     * plain read instead of findByIdForUpdate, the contender never waits and this times out.
     *
     * @return what the contender threw, or null if it succeeded
     */
    private Exception runWhileHoldingRowLock(Runnable holder, Runnable contender) throws Exception {
        CountDownLatch holderHoldsLock = new CountDownLatch(1);
        doAnswer(invocation -> {
            holderHoldsLock.countDown();
            awaitAnotherTransactionBlockedOnALock();
            return invocation.callRealMethod();
        }).when(receiptMapper).toReceiptResponse(any());

        ExecutorService pool = Executors.newFixedThreadPool(2);
        Future<?> holding = pool.submit(holder);
        Future<Exception> contending = pool.submit(() -> {
            holderHoldsLock.await(LOCK_WAIT_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
            try {
                contender.run();
                return null;
            } catch (Exception e) {
                return e;
            }
        });

        holding.get(15, TimeUnit.SECONDS);
        Exception contenderFailure = contending.get(15, TimeUnit.SECONDS);
        pool.shutdown();
        return contenderFailure;
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
