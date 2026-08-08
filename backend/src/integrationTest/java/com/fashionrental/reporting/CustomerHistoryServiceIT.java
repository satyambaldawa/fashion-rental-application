package com.fashionrental.reporting;

import com.fashionrental.AbstractIntegrationTest;
import com.fashionrental.customer.Customer;
import com.fashionrental.customer.CustomerRepository;
import com.fashionrental.customer.model.response.CustomerReceiptResponse;
import com.fashionrental.inventory.Item;
import com.fashionrental.inventory.ItemRepository;
import com.fashionrental.inventory.PackageComponent;
import com.fashionrental.inventory.PackageComponentRepository;
import com.fashionrental.invoice.ReturnService;
import com.fashionrental.invoice.model.request.ProcessReturnRequest;
import com.fashionrental.invoice.model.request.ReturnLineItem;
import com.fashionrental.receipt.CheckoutService;
import com.fashionrental.receipt.model.request.CheckoutLineItem;
import com.fashionrental.receipt.model.request.CheckoutRequest;
import com.fashionrental.receipt.model.response.ReceiptResponse;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Persists a customer with a package rental (billed line + zero-rate component
 * reservation lines, returned with an invoice) and a plain rental (still active) against
 * real PostgreSQL, then verifies {@link CustomerHistoryService#getHistory} round-trips
 * ordering, invoice resolution, and the zero-rate line-item filter correctly.
 */
@Transactional
class CustomerHistoryServiceIT extends AbstractIntegrationTest {

    @Autowired private CustomerHistoryService customerHistoryService;
    @Autowired private CheckoutService checkoutService;
    @Autowired private ReturnService returnService;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private ItemRepository itemRepository;
    @Autowired private PackageComponentRepository packageComponentRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void getHistory_returns_receipts_reverse_chronological_with_invoice_and_filtered_items() {
        Customer customer = new Customer();
        customer.setName("Kavita Rao");
        customer.setPhone("9822233344");
        customer.setCustomerType(Customer.CustomerType.MISC);
        customer.setIsActive(true);
        customer = customerRepository.save(customer);

        Item turban = newIndividualItem("Turban", 0, 0);
        turban = itemRepository.save(turban);

        Item weddingPackage = newIndividualItem("Wedding Package", 1000, 3000);
        weddingPackage.setItemType(Item.ItemType.PACKAGE);
        weddingPackage = itemRepository.save(weddingPackage);

        PackageComponent component = new PackageComponent();
        component.setPackageItem(weddingPackage);
        component.setComponentItem(turban);
        component.setQuantity(2);
        packageComponentRepository.save(component);

        Item sherwani = newIndividualItem("Blue Sherwani", 300, 1000);
        sherwani = itemRepository.save(sherwani);

        OffsetDateTime start = OffsetDateTime.parse("2026-04-10T10:00:00+05:30");
        OffsetDateTime end = OffsetDateTime.parse("2026-04-12T10:00:00+05:30");

        // ── receipt 1: package rental, returned (has an invoice) ──
        ReceiptResponse packageReceipt = checkoutService.createReceipt(new CheckoutRequest(
                customer.getId(), start, end,
                List.of(new CheckoutLineItem(weddingPackage.getId(), 1)),
                null
        ));
        List<ReturnLineItem> returnLines = packageReceipt.lineItems().stream()
                .map(li -> new ReturnLineItem(li.id(), false, null, null))
                .toList();
        returnService.processReturn(packageReceipt.id(), new ProcessReturnRequest(
                end, returnLines, "CASH", null, null
        ));

        // ── receipt 2: plain rental, still active (no invoice) ──
        ReceiptResponse plainReceipt = checkoutService.createReceipt(new CheckoutRequest(
                customer.getId(), start, end,
                List.of(new CheckoutLineItem(sherwani.getId(), 1)),
                null
        ));

        entityManager.flush();
        entityManager.clear();

        CustomerHistoryData history = customerHistoryService.getHistory(customer.getId());

        assertThat(history.receipts()).hasSize(2);
        assertThat(history.outstandingDeposit()).isEqualTo(plainReceipt.totalDeposit());

        CustomerReceiptResponse mostRecent = history.receipts().get(0);
        CustomerReceiptResponse oldest = history.receipts().get(1);
        assertThat(mostRecent.id()).isEqualTo(plainReceipt.id());
        assertThat(oldest.id()).isEqualTo(packageReceipt.id());

        assertThat(mostRecent.status()).isEqualTo("GIVEN");
        assertThat(mostRecent.invoice()).isNull();
        assertThat(mostRecent.items()).extracting("itemName").containsExactly("Blue Sherwani");

        assertThat(oldest.status()).isEqualTo("RETURNED");
        assertThat(oldest.invoice()).isNotNull();
        assertThat(oldest.items())
                .extracting("itemName")
                .containsExactly("Wedding Package");
    }

    private Item newIndividualItem(String name, int rate, int deposit) {
        Item item = new Item();
        item.setName(name);
        item.setCategory(Item.Category.COSTUME);
        item.setItemType(Item.ItemType.INDIVIDUAL);
        item.setRate(rate);
        item.setDeposit(deposit);
        item.setQuantity(5);
        item.setIsActive(true);
        return item;
    }
}
