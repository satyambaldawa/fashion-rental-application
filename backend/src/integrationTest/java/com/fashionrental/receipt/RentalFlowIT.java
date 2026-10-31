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
import com.fashionrental.receipt.model.request.AdHocLineItem;
import com.fashionrental.receipt.model.request.CheckoutLineItem;
import com.fashionrental.receipt.model.request.CheckoutRequest;
import com.fashionrental.receipt.model.response.ReceiptResponse;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
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
    @Autowired private ReceiptGivenService receiptGivenService;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private ItemRepository itemRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @BeforeEach
    void authenticateAsOwner() {
        var auth = new UsernamePasswordAuthenticationToken(
                "owner", null, List.of(new SimpleGrantedAuthority("ROLE_OWNER")));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

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
                List.of(),
                "handle with care",
                null, null, null, null
        ));

        assertThat(receipt.id()).isNotNull();
        assertThat(receipt.status()).isEqualTo("GENERATED");

        // ── hand the dress over; only then can it be returned ──
        assertThat(receiptGivenService.markGiven(receipt.id()).status()).isEqualTo("GIVEN");
        assertThat(receipt.totalDeposit()).isEqualTo(1000);
        assertThat(receipt.lineItems()).hasSize(1);
        UUID receiptLineItemId = receipt.lineItems().get(0).id();

        // ── return on time (no late fee, no damage) ──
        InvoiceResponse invoice = returnService.processReturn(receipt.id(), new ProcessReturnRequest(
                end,
                List.of(new ReturnLineItem(receiptLineItemId, false, null, null)),
                "CASH", null, null, null
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

    @Test
    void ad_hoc_only_rental_completes_checkout_and_return() {
        Customer customer = new Customer();
        customer.setName("Anil");
        customer.setPhone("9800011122");
        customer.setCustomerType(Customer.CustomerType.MISC);
        customer.setIsActive(true);
        customer = customerRepository.save(customer);

        OffsetDateTime start = OffsetDateTime.parse("2026-04-18T10:00:00+05:30");
        OffsetDateTime end = OffsetDateTime.parse("2026-04-21T10:00:00+05:30");

        // ── checkout: no catalogue items, one typed-in product ──
        ReceiptResponse receipt = checkoutService.createReceipt(new CheckoutRequest(
                customer.getId(), start, end,
                List.of(),
                List.of(new AdHocLineItem("Walk-in Lehenga", "Free size", 500, 1000, 1)),
                null,
                null, null, null, null
        ));

        assertThat(receipt.lineItems()).hasSize(1);
        assertThat(receipt.lineItems().get(0).itemName()).isEqualTo("Walk-in Lehenga");
        assertThat(receipt.lineItems().get(0).itemSize()).isEqualTo("Free size");
        assertThat(receipt.lineItems().get(0).lineRent()).isEqualTo(500);
        assertThat(receipt.lineItems().get(0).itemPurchaseRate()).isNull();
        assertThat(receipt.totalRent()).isEqualTo(500);
        assertThat(receipt.totalDeposit()).isEqualTo(1000);

        // ── return on time → full deposit back, product name carries through to the invoice ──
        receiptGivenService.markGiven(receipt.id());
        InvoiceResponse invoice = returnService.processReturn(receipt.id(), new ProcessReturnRequest(
                end,
                List.of(new ReturnLineItem(receipt.lineItems().get(0).id(), false, null, null)),
                "CASH", null, null, null
        ));

        assertThat(invoice.totalLateFee()).isZero();
        assertThat(invoice.depositToReturn()).isEqualTo(1000);
        assertThat(invoice.transactionType()).isEqualTo("REFUND");
        assertThat(invoice.lineItems()).hasSize(1);
        assertThat(invoice.lineItems().get(0).itemName()).isEqualTo("Walk-in Lehenga");
    }

    @Test
    void late_return_with_late_fee_applied_collects_balance() {
        OffsetDateTime end = OffsetDateTime.parse("2026-04-19T10:00:00+05:30");
        ReceiptResponse receipt = checkoutAndGiveItem(end);
        UUID lineItemId = receipt.lineItems().get(0).id();

        InvoiceResponse invoice = returnService.processReturn(receipt.id(), new ProcessReturnRequest(
                end.plusHours(7),
                List.of(new ReturnLineItem(lineItemId, true, null, 800)),
                "CASH", null, null, Boolean.TRUE
        ));
        flushAndClear();

        assertThat(invoice.totalLateFee()).isEqualTo(300);
        InvoiceResponse persisted = returnService.getInvoice(invoice.id());
        assertThat(persisted.totalLateFee()).isEqualTo(300);
        assertThat(persisted.waivedLateFee()).isZero();
        assertThat(persisted.lineItems()).hasSize(1);
        assertThat(persisted.lineItems().get(0).lateFee()).isEqualTo(300);
        assertThat(persisted.totalLateFee())
                .isEqualTo(persisted.lineItems().stream().mapToInt(li -> li.lateFee()).sum());
        assertThat(persisted.transactionType()).isEqualTo("COLLECT");
        assertThat(persisted.finalAmount()).isEqualTo(100);
    }

    @Test
    void late_return_with_late_fee_waived_refunds_and_stores_waived_amount() {
        var auth = new UsernamePasswordAuthenticationToken(
                "staff", null, List.of(new SimpleGrantedAuthority("ROLE_EXECUTIVE")));
        SecurityContextHolder.getContext().setAuthentication(auth);

        OffsetDateTime end = OffsetDateTime.parse("2026-04-19T10:00:00+05:30");
        ReceiptResponse receipt = checkoutAndGiveItem(end);
        UUID lineItemId = receipt.lineItems().get(0).id();

        InvoiceResponse invoice = returnService.processReturn(receipt.id(), new ProcessReturnRequest(
                end.plusHours(7),
                List.of(new ReturnLineItem(lineItemId, true, null, 800)),
                "CASH", null, null, Boolean.FALSE
        ));
        flushAndClear();

        InvoiceResponse persisted = returnService.getInvoice(invoice.id());
        assertThat(persisted.totalLateFee()).isZero();
        assertThat(persisted.waivedLateFee()).isEqualTo(300);
        assertThat(persisted.lineItems()).hasSize(1);
        assertThat(persisted.lineItems().get(0).lateFee()).isZero();
        assertThat(persisted.totalLateFee())
                .isEqualTo(persisted.lineItems().stream().mapToInt(li -> li.lateFee()).sum());
        assertThat(persisted.transactionType()).isEqualTo("REFUND");
        assertThat(persisted.finalAmount()).isEqualTo(200);
    }

    private ReceiptResponse checkoutAndGiveItem(OffsetDateTime end) {
        Customer customer = new Customer();
        customer.setName("Priya");
        customer.setPhone("9833344455");
        customer.setCustomerType(Customer.CustomerType.MISC);
        customer.setIsActive(true);
        customer = customerRepository.save(customer);

        Item item = new Item();
        item.setName("Blue Sherwani");
        item.setCategory(Item.Category.COSTUME);
        item.setItemType(Item.ItemType.INDIVIDUAL);
        item.setRate(300);
        item.setDeposit(1000);
        item.setQuantity(2);
        item.setIsActive(true);
        item = itemRepository.save(item);

        OffsetDateTime start = end.minusDays(1);
        ReceiptResponse receipt = checkoutService.createReceipt(new CheckoutRequest(
                customer.getId(), start, end,
                List.of(new CheckoutLineItem(item.getId(), 1)),
                List.of(), null, null
        ));
        receiptGivenService.markGiven(receipt.id());
        return receipt;
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    @Autowired private ReceiptRepository receiptRepository;

    private Receipt receiptRepositoryFind(UUID id) {
        return receiptRepository.findById(id).orElseThrow();
    }
}
