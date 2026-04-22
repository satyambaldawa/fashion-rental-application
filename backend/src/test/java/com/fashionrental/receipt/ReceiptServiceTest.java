package com.fashionrental.receipt;

import com.fashionrental.common.util.DateTimeUtil;
import com.fashionrental.customer.Customer;
import com.fashionrental.inventory.Item;
import com.fashionrental.receipt.model.response.ReceiptSummaryResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReceiptServiceTest {

    @Mock ReceiptRepository receiptRepository;
    @Mock DateTimeUtil dateTimeUtil;

    @InjectMocks ReceiptService receiptService;

    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-04-21T12:00:00+05:30");

    @Test
    void should_mark_receipt_as_overdue_when_past_end_datetime() {
        OffsetDateTime end = NOW.minusHours(5);
        Receipt receipt = buildReceipt(end, Receipt.Status.GIVEN, "Sherwani", 1);

        when(receiptRepository.findByStatusOrderByEndDatetimeAsc(Receipt.Status.GIVEN))
                .thenReturn(List.of(receipt));
        when(dateTimeUtil.calculateOverdueHours(eq(end), any())).thenReturn(5.0);

        List<ReceiptSummaryResponse> result = receiptService.listReceipts(null, null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).isOverdue()).isTrue();
        assertThat(result.get(0).overdueHours()).isEqualTo(5.0);
    }

    @Test
    void should_not_mark_receipt_as_overdue_when_before_end_datetime() {
        OffsetDateTime end = OffsetDateTime.now().plusDays(5);
        Receipt receipt = buildReceipt(end, Receipt.Status.GIVEN, "Red Saree", 1);

        when(receiptRepository.findByStatusOrderByEndDatetimeAsc(Receipt.Status.GIVEN))
                .thenReturn(List.of(receipt));

        List<ReceiptSummaryResponse> result = receiptService.listReceipts(null, null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).isOverdue()).isFalse();
        assertThat(result.get(0).overdueHours()).isNull();
    }

    @Test
    void should_return_empty_list_when_no_active_receipts() {
        when(receiptRepository.findByStatusOrderByEndDatetimeAsc(Receipt.Status.GIVEN))
                .thenReturn(List.of());

        List<ReceiptSummaryResponse> result = receiptService.listReceipts(null, null);

        assertThat(result).isEmpty();
    }

    @Test
    void should_format_item_names_with_quantity() {
        OffsetDateTime end = NOW.plusDays(2);
        Receipt receipt = buildReceipt(end, Receipt.Status.GIVEN, "Blue Pagdi", 3);

        when(receiptRepository.findByStatusOrderByEndDatetimeAsc(Receipt.Status.GIVEN))
                .thenReturn(List.of(receipt));

        List<ReceiptSummaryResponse> result = receiptService.listReceipts(null, null);

        assertThat(result.get(0).itemNames()).containsExactly("Blue Pagdi \u00d73");
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private Receipt buildReceipt(OffsetDateTime end, Receipt.Status status, String itemName, int qty) {
        Customer customer = new Customer();
        customer.setName("Test Customer");
        customer.setPhone("9000000000");
        customer.setCustomerType(Customer.CustomerType.MISC);
        injectId(customer, Customer.class, UUID.randomUUID());

        Item item = new Item();
        item.setName(itemName);
        item.setCategory(Item.Category.COSTUME);
        item.setRate(200);
        item.setDeposit(1000);
        item.setQuantity(10);
        item.setIsActive(true);
        injectId(item, Item.class, UUID.randomUUID());

        ReceiptLineItem li = new ReceiptLineItem();
        li.setItem(item);
        li.setQuantity(qty);
        li.setRateSnapshot(200);
        li.setDepositSnapshot(1000);
        li.setLineRent(200 * qty);
        li.setLineDeposit(1000 * qty);

        Receipt receipt = new Receipt();
        receipt.setCustomer(customer);
        receipt.setStartDatetime(NOW.minusDays(2));
        receipt.setEndDatetime(end);
        receipt.setRentalDays(2);
        receipt.setStatus(status);
        receipt.setReceiptNumber("R-20260421-001");
        receipt.setTotalRent(200 * qty);
        receipt.setTotalDeposit(1000 * qty);
        receipt.setGrandTotal(1200 * qty);
        receipt.getLineItems().add(li);
        li.setReceipt(receipt);

        injectId(receipt, Receipt.class, UUID.randomUUID());
        injectField(receipt, Receipt.class, "createdAt", NOW.minusDays(2));

        return receipt;
    }

    private <T> void injectId(Object target, Class<T> clazz, UUID id) {
        injectField(target, clazz, "id", id);
    }

    private <T> void injectField(Object target, Class<T> clazz, String fieldName, Object value) {
        try {
            var field = clazz.getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
