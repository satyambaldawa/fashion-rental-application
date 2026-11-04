package com.fashionrental.receipt;

import com.fashionrental.AbstractIntegrationTest;
import com.fashionrental.common.idempotency.IdempotencyLock;
import com.fashionrental.customer.Customer;
import com.fashionrental.customer.CustomerRepository;
import com.fashionrental.inventory.Item;
import com.fashionrental.inventory.ItemRepository;
import com.fashionrental.receipt.model.response.ReceiptResponse;
import com.fashionrental.support.IntegrationFixtures;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

/**
 * Proves checkout idempotency (#189): replaying a key returns the first receipt, and two requests
 * with the same key racing each other produce one receipt instead of a unique-index failure.
 *
 * <p>Deliberately NOT @Transactional: each checkout needs its own real transaction.
 */
class IdempotentCheckoutIT extends AbstractIntegrationTest {

    @Autowired private CheckoutService checkoutService;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private ItemRepository itemRepository;
    @Autowired private ReceiptRepository receiptRepository;

    @MockitoSpyBean private IdempotencyLock idempotencyLock;

    @AfterEach
    void cleanUp() {
        receiptRepository.deleteAll();
        itemRepository.deleteAll();
        customerRepository.deleteAll();
    }

    @Test
    void should_return_the_first_receipt_and_create_no_second_one_when_the_same_key_is_replayed() throws Exception {
        Customer customer = IntegrationFixtures.customer(customerRepository, "Rekha", "9855500011");
        Item item = IntegrationFixtures.item(itemRepository, "Party Gown", 3);
        UUID key = UUID.randomUUID();

        ReceiptResponse first = IntegrationFixtures.asOwner(() -> checkoutService.createReceipt(
                IntegrationFixtures.cashCheckout(customer.getId(), item.getId(), 1, key)));
        ReceiptResponse replay = IntegrationFixtures.asOwner(() -> checkoutService.createReceipt(
                IntegrationFixtures.cashCheckout(customer.getId(), item.getId(), 1, key)));

        assertThat(replay.id()).isEqualTo(first.id());
        assertThat(replay.receiptNumber()).isEqualTo(first.receiptNumber());
        assertThat(receiptRepository.count()).isEqualTo(1);
    }

    @Test
    void should_create_separate_receipts_for_different_keys() throws Exception {
        Customer customer = IntegrationFixtures.customer(customerRepository, "Suresh", "9855500022");
        Item item = IntegrationFixtures.item(itemRepository, "Party Blazer", 3);

        ReceiptResponse first = IntegrationFixtures.asOwner(() -> checkoutService.createReceipt(
                IntegrationFixtures.cashCheckout(customer.getId(), item.getId(), 1, UUID.randomUUID())));
        ReceiptResponse second = IntegrationFixtures.asOwner(() -> checkoutService.createReceipt(
                IntegrationFixtures.cashCheckout(customer.getId(), item.getId(), 1, UUID.randomUUID())));

        assertThat(second.id()).isNotEqualTo(first.id());
        assertThat(receiptRepository.count()).isEqualTo(2);
    }

    @Test
    void should_produce_one_receipt_and_no_error_when_two_requests_with_the_same_key_are_in_flight_together() throws Exception {
        Customer customer = IntegrationFixtures.customer(customerRepository, "Meera", "9855500033");
        Item item = IntegrationFixtures.item(itemRepository, "Royal Sherwani", 3);
        UUID key = UUID.randomUUID();

        // Both threads must reach the lock before either takes it. The barrier sits BEFORE the
        // real lock call; putting it after (e.g. on calculateRentalDays) would deadlock, because
        // by then the first thread would be holding the lock the second is waiting for.
        CountDownLatch bothAtTheLock = new CountDownLatch(2);
        // Stub the spy behind the transactional proxy: stubbing through the proxy would invoke
        // lock() under @Transactional(MANDATORY) on this test thread, which has no transaction.
        IdempotencyLock lockSpy = AopTestUtils.getUltimateTargetObject(idempotencyLock);
        doAnswer(invocation -> {
            bothAtTheLock.countDown();
            if (!bothAtTheLock.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Test barrier timed out — the other thread never arrived");
            }
            return invocation.callRealMethod();
        }).when(lockSpy).lock(any());

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<ReceiptResponse> a = pool.submit(() -> IntegrationFixtures.asOwner(() -> checkoutService.createReceipt(
                    IntegrationFixtures.cashCheckout(customer.getId(), item.getId(), 1, key))));
            Future<ReceiptResponse> b = pool.submit(() -> IntegrationFixtures.asOwner(() -> checkoutService.createReceipt(
                    IntegrationFixtures.cashCheckout(customer.getId(), item.getId(), 1, key))));

            List<ReceiptResponse> responses = List.of(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS));

            assertThat(responses.get(0).id()).isEqualTo(responses.get(1).id());
            assertThat(receiptRepository.count()).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }
}
