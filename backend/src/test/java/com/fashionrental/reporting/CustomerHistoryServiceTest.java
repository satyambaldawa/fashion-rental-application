package com.fashionrental.reporting;

import com.fashionrental.customer.Customer;
import com.fashionrental.customer.model.response.CustomerReceiptResponse;
import com.fashionrental.invoice.Invoice;
import com.fashionrental.invoice.InvoiceRepository;
import com.fashionrental.receipt.Receipt;
import com.fashionrental.receipt.ReceiptLineItem;
import com.fashionrental.receipt.ReceiptRepository;
import com.fashionrental.support.TestData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerHistoryServiceTest {

    @Mock
    private ReceiptRepository receiptRepository;

    @Mock
    private InvoiceRepository invoiceRepository;

    @InjectMocks
    private CustomerHistoryService customerHistoryService;

    private final OffsetDateTime start = OffsetDateTime.parse("2026-04-10T10:00:00+05:30");
    private final OffsetDateTime end = OffsetDateTime.parse("2026-04-12T10:00:00+05:30");

    @Test
    void should_return_empty_receipts_and_zero_outstanding_when_customer_has_no_receipts() {
        UUID customerId = UUID.randomUUID();
        when(receiptRepository.findByCustomer_IdOrderByCreatedAtDesc(customerId)).thenReturn(List.of());

        CustomerHistoryData result = customerHistoryService.getHistory(customerId);

        assertThat(result.outstandingDeposit()).isZero();
        assertThat(result.receipts()).isEmpty();
        verify(invoiceRepository, never()).findByReceipt_IdIn(anyList());
    }

    @Test
    void should_sum_outstanding_deposit_across_given_receipts_only() {
        UUID customerId = UUID.randomUUID();
        Customer customer = TestData.customer();
        Receipt given1 = TestData.receipt(customer, start, end, TestData.receiptLineItem(TestData.item("Sherwani", 300, 1000), 1));
        Receipt given2 = TestData.receipt(customer, start, end, TestData.receiptLineItem(TestData.item("Lehenga", 200, 500), 1));
        Receipt returned = TestData.receipt(customer, start, end, TestData.receiptLineItem(TestData.item("Turban", 100, 300), 1));
        returned.setStatus(Receipt.Status.RETURNED);
        when(receiptRepository.findByCustomer_IdOrderByCreatedAtDesc(customerId))
                .thenReturn(List.of(given1, given2, returned));
        when(invoiceRepository.findByReceipt_IdIn(anyList())).thenReturn(List.of());

        CustomerHistoryData result = customerHistoryService.getHistory(customerId);

        assertThat(result.outstandingDeposit()).isEqualTo(1500);
    }

    @Test
    void should_include_invoice_summary_on_returned_receipt_and_null_on_given_receipt() {
        UUID customerId = UUID.randomUUID();
        Customer customer = TestData.customer();
        Receipt returned = TestData.receipt(customer, start, end, TestData.receiptLineItem(TestData.item("Sherwani", 300, 1000), 1));
        returned.setStatus(Receipt.Status.RETURNED);
        Receipt given = TestData.receipt(customer, start, end, TestData.receiptLineItem(TestData.item("Lehenga", 200, 500), 1));
        Invoice invoice = TestData.invoice(Invoice.TransactionType.REFUND, 950, 0, 0);
        invoice.setInvoiceNumber("INV-20260412-001");
        invoice.setReceipt(returned);
        when(receiptRepository.findByCustomer_IdOrderByCreatedAtDesc(customerId))
                .thenReturn(List.of(returned, given));
        when(invoiceRepository.findByReceipt_IdIn(anyList())).thenReturn(List.of(invoice));

        CustomerHistoryData result = customerHistoryService.getHistory(customerId);

        CustomerReceiptResponse returnedResponse = result.receipts().stream()
                .filter(r -> r.status().equals("RETURNED")).findFirst().orElseThrow();
        CustomerReceiptResponse givenResponse = result.receipts().stream()
                .filter(r -> r.status().equals("GIVEN")).findFirst().orElseThrow();

        assertThat(returnedResponse.invoice()).isNotNull();
        assertThat(returnedResponse.invoice().invoiceNumber()).isEqualTo("INV-20260412-001");
        assertThat(returnedResponse.invoice().finalAmount()).isEqualTo(950);
        assertThat(returnedResponse.invoice().transactionType()).isEqualTo("REFUND");
        assertThat(givenResponse.invoice()).isNull();
    }

    @Test
    void should_exclude_zero_rate_line_items_from_items_list() {
        UUID customerId = UUID.randomUUID();
        Customer customer = TestData.customer();
        ReceiptLineItem packageLine = TestData.receiptLineItem(TestData.item("Wedding Package", 1000, 3000), 1);
        ReceiptLineItem componentLine = TestData.receiptLineItem(TestData.item("Component Turban", 0, 0), 1);
        Receipt receipt = TestData.receipt(customer, start, end, packageLine, componentLine);
        when(receiptRepository.findByCustomer_IdOrderByCreatedAtDesc(customerId)).thenReturn(List.of(receipt));
        when(invoiceRepository.findByReceipt_IdIn(anyList())).thenReturn(List.of());

        CustomerHistoryData result = customerHistoryService.getHistory(customerId);

        List<String> itemNames = result.receipts().get(0).items().stream()
                .map(i -> i.itemName()).toList();
        assertThat(itemNames).containsExactly("Wedding Package");
    }

    @Test
    void should_return_empty_items_list_when_every_line_item_is_zero_rate() {
        UUID customerId = UUID.randomUUID();
        Customer customer = TestData.customer();
        ReceiptLineItem orphanedComponent = TestData.receiptLineItem(TestData.item("Orphaned Component", 0, 0), 1);
        Receipt receipt = TestData.receipt(customer, start, end, orphanedComponent);
        when(receiptRepository.findByCustomer_IdOrderByCreatedAtDesc(customerId)).thenReturn(List.of(receipt));
        when(invoiceRepository.findByReceipt_IdIn(anyList())).thenReturn(List.of());

        CustomerHistoryData result = customerHistoryService.getHistory(customerId);

        assertThat(result.receipts()).hasSize(1);
        assertThat(result.receipts().get(0).items()).isEmpty();
    }

    @Test
    void should_include_individually_rented_items_with_real_pricing() {
        UUID customerId = UUID.randomUUID();
        Customer customer = TestData.customer();
        ReceiptLineItem sherwani = TestData.receiptLineItem(TestData.item("Sherwani", 300, 1000), 1);
        ReceiptLineItem turban = TestData.receiptLineItem(TestData.item("Turban", 100, 200), 2);
        Receipt receipt = TestData.receipt(customer, start, end, sherwani, turban);
        when(receiptRepository.findByCustomer_IdOrderByCreatedAtDesc(customerId)).thenReturn(List.of(receipt));
        when(invoiceRepository.findByReceipt_IdIn(anyList())).thenReturn(List.of());

        CustomerHistoryData result = customerHistoryService.getHistory(customerId);

        assertThat(result.receipts().get(0).items()).hasSize(2);
        assertThat(result.receipts().get(0).items())
                .extracting("itemName", "quantity")
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("Sherwani", 1),
                        org.assertj.core.groups.Tuple.tuple("Turban", 2)
                );
    }
}
