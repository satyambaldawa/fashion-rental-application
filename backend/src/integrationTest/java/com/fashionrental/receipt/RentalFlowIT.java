package com.fashionrental.receipt;

import com.fashionrental.AbstractIntegrationTest;
import com.fashionrental.customer.Customer;
import com.fashionrental.customer.CustomerRepository;
import com.fashionrental.inventory.Item;
import com.fashionrental.inventory.ItemRepository;
import com.fashionrental.invoice.ReturnService;
import com.fashionrental.invoice.model.request.ProcessReturnRequest;
import com.fashionrental.invoice.model.request.ReturnLineItem;
import com.fashionrental.invoice.model.response.InvoiceResponse;
import com.fashionrental.receipt.model.request.CheckoutLineItem;
import com.fashionrental.receipt.model.request.CheckoutRequest;
import com.fashionrental.receipt.model.response.ReceiptResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Happy-path integration test for the full rental lifecycle against real PostgreSQL:
 * checkout → receipt → return → invoice. Exercises CheckoutService, ReturnService, the
 * mappers, and the repository layer end-to-end within real transactions.
 */
@Transactional
class RentalFlowIT extends AbstractIntegrationTest {

    @Autowired private CheckoutService checkoutService;
    @Autowired private ReturnService returnService;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private ItemRepository itemRepository;

    @Test
    void checkout_then_return_on_time_refunds_the_full_deposit() {
        Customer customer = new Customer();
        customer.setName("Meera");
        customer.setPhone("9811122233");
        customer.setCustomerType(Customer.CustomerType.MISC);
        customer.setIsActive(true);
        customer = customerRepository.save(customer);

        Item item = new Item();
        item.setName("Royal Sherwani");
        item.setCategory(Item.Category.COSTUME);
        item.setItemType(Item.ItemType.INDIVIDUAL);
        item.setRate(300);
        item.setDeposit(1000);
        item.setQuantity(2);
        item.setIsActive(true);
        item = itemRepository.save(item);

        OffsetDateTime start = OffsetDateTime.parse("2026-04-18T10:00:00+05:30");
        OffsetDateTime end = OffsetDateTime.parse("2026-04-19T10:00:00+05:30");

        // ── checkout ──
        ReceiptResponse receipt = checkoutService.createReceipt(new CheckoutRequest(
                customer.getId(), start, end,
                List.of(new CheckoutLineItem(item.getId(), 1)),
                "handle with care"
        ));

        assertThat(receipt.id()).isNotNull();
        assertThat(receipt.status()).isEqualTo("GIVEN");
        assertThat(receipt.totalDeposit()).isEqualTo(1000);
        assertThat(receipt.lineItems()).hasSize(1);
        UUID receiptLineItemId = receipt.lineItems().get(0).id();

        // ── return on time (no late fee, no damage) ──
        InvoiceResponse invoice = returnService.processReturn(receipt.id(), new ProcessReturnRequest(
                end,
                List.of(new ReturnLineItem(receiptLineItemId, false, null, null)),
                "CASH", null, null
        ));

        assertThat(invoice.invoiceNumber()).isNotBlank();
        assertThat(invoice.totalLateFee()).isZero();
        assertThat(invoice.totalDamageCost()).isZero();
        assertThat(invoice.depositToReturn()).isEqualTo(1000);
        assertThat(invoice.transactionType()).isEqualTo("REFUND");

        // ── receipt is now marked returned ──
        Receipt persisted = receiptRepositoryFind(receipt.id());
        assertThat(persisted.getStatus()).isEqualTo(Receipt.Status.RETURNED);
    }

    @Autowired private ReceiptRepository receiptRepository;

    private Receipt receiptRepositoryFind(UUID id) {
        return receiptRepository.findById(id).orElseThrow();
    }
}
