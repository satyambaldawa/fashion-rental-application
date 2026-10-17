package com.fashionrental.receipt;

import com.fashionrental.common.exception.ConflictException;
import com.fashionrental.common.exception.ResourceNotFoundException;
import com.fashionrental.customer.Customer;
import com.fashionrental.receipt.model.response.ReceiptResponse;
import com.fashionrental.support.TestData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReceiptGivenServiceTest {

    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-10-17T10:00:00+05:30");
    private static final Clock FIXED_CLOCK = Clock.fixed(NOW.toInstant(), ZoneId.of("Asia/Kolkata"));

    @Mock private ReceiptRepository receiptRepository;

    private Receipt receipt;
    private ReceiptGivenService service;

    @BeforeEach
    void setUp() {
        Customer customer = TestData.customer("Asha", "9812345678");
        receipt = TestData.receipt(customer, NOW.plusDays(2), NOW.plusDays(2).plusHours(1),
                TestData.receiptLineItem(TestData.item("Sherwani", 300, 1000), 1));
        receipt.setStatus(Receipt.Status.GENERATED);
        service = new ReceiptGivenService(receiptRepository, new ReceiptMapper(), FIXED_CLOCK);
    }

    @Test
    void should_mark_generated_receipt_as_given_and_record_when() {
        when(receiptRepository.findByIdForUpdate(receipt.getId())).thenReturn(Optional.of(receipt));
        when(receiptRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ReceiptResponse response = service.markGiven(receipt.getId());

        assertThat(response.status()).isEqualTo("GIVEN");
        assertThat(receipt.getGivenAt()).isEqualTo(NOW);
    }

    @Test
    void should_lock_the_receipt_row_so_a_concurrent_cancel_cannot_be_overwritten() {
        when(receiptRepository.findByIdForUpdate(receipt.getId())).thenReturn(Optional.of(receipt));
        when(receiptRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.markGiven(receipt.getId());

        verify(receiptRepository).findByIdForUpdate(receipt.getId());
        verify(receiptRepository, never()).findById(any());
    }

    @Test
    void should_leave_given_receipt_untouched_when_marked_again() {
        OffsetDateTime originalGivenAt = NOW.minusHours(3);
        receipt.markGiven(originalGivenAt);
        when(receiptRepository.findByIdForUpdate(receipt.getId())).thenReturn(Optional.of(receipt));

        ReceiptResponse response = service.markGiven(receipt.getId());

        assertThat(response.status()).isEqualTo("GIVEN");
        assertThat(receipt.getGivenAt()).isEqualTo(originalGivenAt);
        verify(receiptRepository, never()).save(any());
    }

    @Test
    void should_reject_cancelled_receipt() {
        receipt.cancel(TestData.owner(), NOW.minusDays(1), Receipt.CancellationReason.WRONG_ORDER, null);
        when(receiptRepository.findByIdForUpdate(receipt.getId())).thenReturn(Optional.of(receipt));

        assertThatThrownBy(() -> service.markGiven(receipt.getId()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("is CANCELLED and cannot be marked as given");

        verify(receiptRepository, never()).save(any());
    }

    @Test
    void should_reject_returned_receipt() {
        receipt.setStatus(Receipt.Status.RETURNED);
        when(receiptRepository.findByIdForUpdate(receipt.getId())).thenReturn(Optional.of(receipt));

        assertThatThrownBy(() -> service.markGiven(receipt.getId()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("is RETURNED and cannot be marked as given");
    }

    @Test
    void should_throw_not_found_for_unknown_receipt() {
        UUID missingId = UUID.randomUUID();
        when(receiptRepository.findByIdForUpdate(missingId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.markGiven(missingId))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
