package com.fashionrental.receipt;

import com.fashionrental.AbstractIntegrationTest;
import com.fashionrental.common.util.DateTimeUtil;
import com.fashionrental.customer.Customer;
import com.fashionrental.customer.CustomerRepository;
import com.fashionrental.inventory.Item;
import com.fashionrental.inventory.ItemRepository;
import com.fashionrental.invoice.BillingService;
import com.fashionrental.invoice.InvoiceRepository;
import com.fashionrental.invoice.ReturnService;
import com.fashionrental.invoice.model.request.ProcessReturnRequest;
import com.fashionrental.invoice.model.request.ReturnLineItem;
import com.fashionrental.receipt.model.response.ReceiptResponse;
import com.fashionrental.support.IntegrationFixtures;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;

/**
 * Proves the atomic daily counter (#189): ten checkouts (and ten returns) forced to overlap all
 * receive distinct numbers in the existing format. With the old COUNT(*)+1 scheme they computed
 * the same number and all but one hit the unique index.
 *
 * <p>Each worker is held at a latch that sits before number generation, so all ten reach the
 * counter together rather than finishing one at a time. Needs a Hikari pool of at least ten
 * (set in application-integrationtest.yml).
 *
 * <p>Deliberately NOT @Transactional: every call needs its own real transaction.
 */
class ReceiptNumberConcurrencyIT extends AbstractIntegrationTest {

    private static final int PARALLEL_CALLS = 10;

    @Autowired private CheckoutService checkoutService;
    @Autowired private ReturnService returnService;
    @Autowired private ReceiptGivenService receiptGivenService;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private ItemRepository itemRepository;
    @Autowired private ReceiptRepository receiptRepository;
    @Autowired private InvoiceRepository invoiceRepository;

    @MockitoSpyBean private DateTimeUtil dateTimeUtil;
    @MockitoSpyBean private BillingService billingService;

    @AfterEach
    void cleanUp() {
        invoiceRepository.deleteAll();
        receiptRepository.deleteAll();
        itemRepository.deleteAll();
        customerRepository.deleteAll();
    }

    @Test
    void should_give_ten_parallel_checkouts_ten_distinct_receipt_numbers() throws Exception {
        List<Callable<String>> checkouts = new ArrayList<>();
        for (int i = 0; i < PARALLEL_CALLS; i++) {
            Customer customer = IntegrationFixtures.customer(customerRepository, "Customer " + i, "98333000" + String.format("%02d", i));
            Item item = IntegrationFixtures.item(itemRepository, "Gown " + i, 1);
            checkouts.add(() -> IntegrationFixtures.asOwner(() -> checkoutService.createReceipt(
                    IntegrationFixtures.cashCheckout(customer.getId(), item.getId(), 1, null)).receiptNumber()));
        }
        holdAllCallersAtLatchBeforeNumbering();

        List<String> numbers = runInParallel(checkouts);

        assertThat(numbers).hasSize(PARALLEL_CALLS).doesNotHaveDuplicates()
                .allMatch(number -> number.matches("^R-\\d{8}-\\d{3}$"));
        assertThat(receiptRepository.count()).isEqualTo(PARALLEL_CALLS);
    }

    @Test
    void should_give_ten_parallel_returns_ten_distinct_invoice_numbers() throws Exception {
        List<Callable<String>> returns = new ArrayList<>();
        for (int i = 0; i < PARALLEL_CALLS; i++) {
            Customer customer = IntegrationFixtures.customer(customerRepository, "Renter " + i, "98444000" + String.format("%02d", i));
            Item item = IntegrationFixtures.item(itemRepository, "Lehenga " + i, 1);
            ReceiptResponse receipt = IntegrationFixtures.asOwner(() -> {
                ReceiptResponse created = checkoutService.createReceipt(
                        IntegrationFixtures.cashCheckout(customer.getId(), item.getId(), 1, null));
                receiptGivenService.markGiven(created.id());
                return created;
            });
            ProcessReturnRequest request = new ProcessReturnRequest(
                    IntegrationFixtures.RENTAL_END,
                    List.of(new ReturnLineItem(receipt.lineItems().get(0).id(), false, null, null)),
                    "CASH", null, null, null);
            returns.add(() -> IntegrationFixtures.asOwner(
                    () -> returnService.processReturn(receipt.id(), request).invoiceNumber()));
        }
        holdAllCallersAtLatchBeforeInvoiceNumbering();

        List<String> numbers = runInParallel(returns);

        assertThat(numbers).hasSize(PARALLEL_CALLS).doesNotHaveDuplicates()
                .allMatch(number -> number.matches("^INV-\\d{8}-\\d{3}$"));
        assertThat(invoiceRepository.count()).isEqualTo(PARALLEL_CALLS);
    }

    private void holdAllCallersAtLatchBeforeNumbering() {
        CountDownLatch allArrived = new CountDownLatch(PARALLEL_CALLS);
        doAnswer(invocation -> {
            awaitAllCallers(allArrived);
            return invocation.callRealMethod();
        }).when(dateTimeUtil).calculateRentalDays(any(), any());
    }

    // calculateLateFee runs before generateInvoiceNumber inside ReturnService.processReturn.
    private void holdAllCallersAtLatchBeforeInvoiceNumbering() {
        CountDownLatch allArrived = new CountDownLatch(PARALLEL_CALLS);
        doAnswer(invocation -> {
            awaitAllCallers(allArrived);
            return invocation.callRealMethod();
        }).when(billingService).calculateLateFee(any(), any(), anyInt(), anyInt(), any());
    }

    private void awaitAllCallers(CountDownLatch allArrived) throws InterruptedException {
        allArrived.countDown();
        if (!allArrived.await(20, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Test barrier timed out — not every caller arrived");
        }
    }

    private List<String> runInParallel(List<Callable<String>> calls) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(PARALLEL_CALLS);
        try {
            List<Future<String>> futures = calls.stream().map(call -> pool.submit(call)).toList();
            List<String> results = new ArrayList<>();
            for (Future<String> future : futures) {
                results.add(future.get(30, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }
}
