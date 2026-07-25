package com.fashionrental.invoice;

import com.fashionrental.common.exception.ConflictException;
import com.fashionrental.common.exception.ResourceNotFoundException;
import com.fashionrental.common.util.ShareTokenService;
import com.fashionrental.configuration.LateFeeRuleRepository;
import com.fashionrental.customer.Customer;
import com.fashionrental.inventory.Item;
import com.fashionrental.invoice.model.request.ProcessReturnRequest;
import com.fashionrental.invoice.model.request.ReturnLineItem;
import com.fashionrental.invoice.model.response.InvoiceResponse;
import com.fashionrental.invoice.model.response.ReturnPreviewResponse;
import com.fashionrental.receipt.Receipt;
import com.fashionrental.receipt.ReceiptLineItem;
import com.fashionrental.receipt.ReceiptRepository;
import com.fashionrental.support.TestData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReturnServiceTest {

    @Mock private ReceiptRepository receiptRepository;
    @Mock private InvoiceRepository invoiceRepository;
    @Mock private LateFeeRuleRepository lateFeeRuleRepository;
    @Mock private BillingService billingService;
    @Mock private InvoiceNumberService invoiceNumberService;
    @Mock private ShareTokenService shareTokenService;
    @InjectMocks private ReturnService returnService;

    private Customer customer;
    private Item item;
    private ReceiptLineItem lineItem;
    private Receipt receipt;

    @BeforeEach
    void setUp() {
        customer = TestData.customer("Asha", "9812345678");
        item = TestData.item("Sherwani", 300, 1000);
        lineItem = TestData.receiptLineItem(item, 1);
        OffsetDateTime start = OffsetDateTime.parse("2026-04-10T10:00:00+05:30");
        OffsetDateTime end = OffsetDateTime.parse("2026-04-11T10:00:00+05:30");
        receipt = TestData.receipt(customer, start, end, lineItem); // deposit 1000
    }

    private ProcessReturnRequest returnRequest(boolean damaged) {
        return new ProcessReturnRequest(
                OffsetDateTime.parse("2026-04-11T12:00:00+05:30"),
                List.of(new ReturnLineItem(lineItem.getId(), damaged, null, null)),
                "CASH", null, null
        );
    }

    @Test
    void previewReturn_returns_full_deposit_when_no_fees() {
        when(receiptRepository.findById(receipt.getId())).thenReturn(Optional.of(receipt));
        when(lateFeeRuleRepository.findByIsActiveTrueOrderBySortOrderAsc()).thenReturn(List.of());
        // billingService returns 0 (mock default) for both fees

        ReturnPreviewResponse preview = returnService.previewReturn(receipt.getId(), returnRequest(false));

        assertThat(preview.totalLateFee()).isZero();
        assertThat(preview.totalDamageCost()).isZero();
        assertThat(preview.depositToReturn()).isEqualTo(1000);
        assertThat(preview.finalAmount()).isEqualTo(1000);
        assertThat(preview.transactionType()).isEqualTo("REFUND");
        assertThat(preview.lineItems()).hasSize(1);
    }

    @Test
    void previewReturn_ignores_return_lines_that_do_not_belong_to_the_receipt() {
        when(receiptRepository.findById(receipt.getId())).thenReturn(Optional.of(receipt));
        when(lateFeeRuleRepository.findByIsActiveTrueOrderBySortOrderAsc()).thenReturn(List.of());
        ProcessReturnRequest strayLine = new ProcessReturnRequest(
                OffsetDateTime.parse("2026-04-11T12:00:00+05:30"),
                List.of(new ReturnLineItem(UUID.randomUUID(), false, null, null)),
                "CASH", null, null
        );

        ReturnPreviewResponse preview = returnService.previewReturn(receipt.getId(), strayLine);

        assertThat(preview.lineItems()).isEmpty();
        assertThat(preview.depositToReturn()).isEqualTo(1000);
    }

    @ParameterizedTest(name = "lateFee={0}, damage={1} → {4} of {3}")
    @CsvSource({
            // lateFee, damage, deposit(1000), expectedFinal, expectedType
            "0,   0,   1000, 1000, REFUND",   // on time → full refund
            "200, 100, 1000, 700,  REFUND",   // deductions < deposit → partial refund
            "1000,0,   1000, 0,    REFUND",   // deductions == deposit → nothing to refund
            "900, 300, 1000, 200,  COLLECT",  // deductions > deposit → collect balance
    })
    void processReturn_computes_deposit_settlement(int lateFee, int damage, int deposit,
                                                   int expectedFinal, String expectedType) {
        when(receiptRepository.findById(receipt.getId())).thenReturn(Optional.of(receipt));
        when(lateFeeRuleRepository.findByIsActiveTrueOrderBySortOrderAsc()).thenReturn(List.of());
        when(invoiceNumberService.generateInvoiceNumber()).thenReturn("INV-2026-0001");
        when(shareTokenService.generate()).thenReturn("share-token");
        when(billingService.calculateLateFee(any(), any(), anyInt(), anyInt(), any())).thenReturn(lateFee);
        when(billingService.calculateDamageCost(any(), any())).thenReturn(damage);
        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(inv -> inv.getArgument(0));

        InvoiceResponse response = returnService.processReturn(receipt.getId(), returnRequest(damage > 0));

        assertThat(response.totalLateFee()).isEqualTo(lateFee);
        assertThat(response.totalDamageCost()).isEqualTo(damage);
        assertThat(response.finalAmount()).isEqualTo(expectedFinal);
        assertThat(response.transactionType()).isEqualTo(expectedType);
    }

    @Test
    void processReturn_marks_receipt_returned_and_persists_invoice() {
        when(receiptRepository.findById(receipt.getId())).thenReturn(Optional.of(receipt));
        when(lateFeeRuleRepository.findByIsActiveTrueOrderBySortOrderAsc()).thenReturn(List.of());
        when(invoiceNumberService.generateInvoiceNumber()).thenReturn("INV-2026-0002");
        when(shareTokenService.generate()).thenReturn("share-token");
        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(inv -> inv.getArgument(0));

        returnService.processReturn(receipt.getId(), returnRequest(false));

        assertThat(receipt.getStatus()).isEqualTo(Receipt.Status.RETURNED);
        verify(receiptRepository).save(receipt);
        ArgumentCaptor<Invoice> captor = ArgumentCaptor.forClass(Invoice.class);
        verify(invoiceRepository).save(captor.capture());
        assertThat(captor.getValue().getInvoiceNumber()).isEqualTo("INV-2026-0002");
        assertThat(captor.getValue().getLineItems()).hasSize(1);
    }

    @ParameterizedTest
    @CsvSource({"UPI,UPI", "OTHER,OTHER", "CASH,CASH", "garbage,CASH"})
    void processReturn_parses_payment_method_and_falls_back_to_cash(String input, String expected) {
        when(receiptRepository.findById(receipt.getId())).thenReturn(Optional.of(receipt));
        when(lateFeeRuleRepository.findByIsActiveTrueOrderBySortOrderAsc()).thenReturn(List.of());
        when(invoiceNumberService.generateInvoiceNumber()).thenReturn("INV-2026-0003");
        when(shareTokenService.generate()).thenReturn("share-token");
        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(inv -> inv.getArgument(0));
        ProcessReturnRequest request = new ProcessReturnRequest(
                OffsetDateTime.parse("2026-04-11T12:00:00+05:30"),
                List.of(new ReturnLineItem(lineItem.getId(), false, null, null)),
                input, null, null
        );

        InvoiceResponse response = returnService.processReturn(receipt.getId(), request);

        assertThat(response.paymentMethod()).isEqualTo(expected);
    }

    @Test
    void loadActiveReceipt_throws_when_receipt_missing() {
        UUID missingId = UUID.randomUUID();
        when(receiptRepository.findById(missingId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> returnService.previewReturn(missingId, returnRequest(false)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Receipt not found");
    }

    @Test
    void processReturn_rejects_already_returned_receipt() {
        receipt.setStatus(Receipt.Status.RETURNED);
        when(receiptRepository.findById(receipt.getId())).thenReturn(Optional.of(receipt));

        assertThatThrownBy(() -> returnService.processReturn(receipt.getId(), returnRequest(false)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already been returned");
    }

    @Test
    void getInvoice_returns_response_when_found() {
        Invoice invoice = new Invoice();
        invoice.setInvoiceNumber("INV-2026-0004");
        invoice.setShareToken("token");
        invoice.setReceipt(receipt);
        invoice.setCustomer(customer);
        invoice.setReturnDatetime(OffsetDateTime.parse("2026-04-11T12:00:00+05:30"));
        invoice.setTransactionType(Invoice.TransactionType.REFUND);
        invoice.setPaymentMethod(Invoice.PaymentMethod.CASH);
        UUID invoiceId = UUID.randomUUID();
        when(invoiceRepository.findById(invoiceId)).thenReturn(Optional.of(invoice));

        InvoiceResponse response = returnService.getInvoice(invoiceId);

        assertThat(response.invoiceNumber()).isEqualTo("INV-2026-0004");
        assertThat(response.customerName()).isEqualTo("Asha");
    }

    @Test
    void getInvoice_throws_when_missing() {
        UUID missingId = UUID.randomUUID();
        when(invoiceRepository.findById(missingId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> returnService.getInvoice(missingId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Invoice not found");
    }
}
