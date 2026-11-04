package com.fashionrental.receipt;

import com.fashionrental.AbstractIntegrationTest;
import com.fashionrental.common.util.DateTimeUtil;
import com.fashionrental.customer.Customer;
import com.fashionrental.customer.CustomerRepository;
import com.fashionrental.inventory.Item;
import com.fashionrental.inventory.ItemRepository;
import com.fashionrental.inventory.PackageComponentRepository;
import com.fashionrental.invoice.InvoiceRepository;
import com.fashionrental.receipt.model.request.CheckoutRequest;
import com.fashionrental.support.IntegrationFixtures;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

/**
 * Proves the item row locks taken at checkout (#189): two checkouts racing for the last unit
 * must produce exactly one receipt and one clear "no longer has enough units" rejection.
 *
 * <p>Both threads are held at DateTimeUtil#calculateRentalDays, which runs before the lock, so
 * they enter the locked section at the same moment. Without the lock both pass the
 * availability re-check and both receipts are created.
 *
 * <p>Deliberately NOT @Transactional: each checkout needs its own real transaction.
 */
class CheckoutLastUnitConcurrencyIT extends AbstractIntegrationTest {

    private static final String LOSER_MESSAGE = "no longer has enough units";

    @Autowired private CheckoutService checkoutService;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private ItemRepository itemRepository;
    @Autowired private PackageComponentRepository packageComponentRepository;
    @Autowired private ReceiptRepository receiptRepository;
    @Autowired private InvoiceRepository invoiceRepository;

    @MockitoSpyBean private DateTimeUtil dateTimeUtil;

    @AfterEach
    void cleanUp() {
        invoiceRepository.deleteAll();
        receiptRepository.deleteAll();
        packageComponentRepository.deleteAll();
        itemRepository.deleteAll();
        customerRepository.deleteAll();
    }

    @Test
    void should_let_only_one_of_two_concurrent_checkouts_take_the_last_unit() throws Exception {
        Customer first = IntegrationFixtures.customer(customerRepository, "Rekha", "9822200011");
        Customer second = IntegrationFixtures.customer(customerRepository, "Suresh", "9822200022");
        Item lastUnit = IntegrationFixtures.item(itemRepository, "Party Gown", 1);

        List<Outcome> outcomes = runTogether(
                () -> checkout(IntegrationFixtures.cashCheckout(first.getId(), lastUnit.getId(), 1, null)),
                () -> checkout(IntegrationFixtures.cashCheckout(second.getId(), lastUnit.getId(), 1, null)));

        assertExactlyOneWinnerAndOneClearRejection(outcomes);
        assertThat(receiptRepository.count()).isEqualTo(1);
    }

    @Test
    void should_not_let_a_package_checkout_and_a_standalone_checkout_of_its_component_both_take_the_last_unit() throws Exception {
        Customer first = IntegrationFixtures.customer(customerRepository, "Meera", "9822200033");
        Customer second = IntegrationFixtures.customer(customerRepository, "Anil", "9822200044");
        Item component = IntegrationFixtures.item(itemRepository, "Turban", 1);
        Item wedding = IntegrationFixtures.packageOf(
                itemRepository, packageComponentRepository, "Wedding Set", 1, component, 1);

        List<Outcome> outcomes = runTogether(
                () -> checkout(IntegrationFixtures.cashCheckout(first.getId(), wedding.getId(), 1, null)),
                () -> checkout(IntegrationFixtures.cashCheckout(second.getId(), component.getId(), 1, null)));

        assertExactlyOneWinnerAndOneClearRejection(outcomes);
        assertThat(receiptRepository.count()).isEqualTo(1);
    }

    private void assertExactlyOneWinnerAndOneClearRejection(List<Outcome> outcomes) {
        long winners = outcomes.stream().filter(Outcome::succeeded).count();
        long clearRejections = outcomes.stream()
                .filter(o -> !o.succeeded() && o.failureMessage() != null && o.failureMessage().contains(LOSER_MESSAGE))
                .count();

        assertThat(winners).as("exactly one checkout should get the last unit").isEqualTo(1);
        assertThat(clearRejections).as("the other must be told the unit is no longer available").isEqualTo(1);
    }

    private List<Outcome> runTogether(Callable<Outcome> first, Callable<Outcome> second) throws Exception {
        CountDownLatch bothArrived = new CountDownLatch(2);
        doAnswer(invocation -> {
            bothArrived.countDown();
            if (!bothArrived.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Test barrier timed out — the other thread never arrived");
            }
            return invocation.callRealMethod();
        }).when(dateTimeUtil).calculateRentalDays(any(), any());

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Outcome> futureFirst = pool.submit(first);
            Future<Outcome> futureSecond = pool.submit(second);
            return List.of(futureFirst.get(20, TimeUnit.SECONDS), futureSecond.get(20, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
        }
    }

    private Outcome checkout(CheckoutRequest request) throws Exception {
        return IntegrationFixtures.asOwner(() -> {
            try {
                checkoutService.createReceipt(request);
                return new Outcome(true, null);
            } catch (Exception e) {
                return new Outcome(false, e.getMessage());
            }
        });
    }

    private record Outcome(boolean succeeded, String failureMessage) {}
}
