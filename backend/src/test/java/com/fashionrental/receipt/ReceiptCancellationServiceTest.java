package com.fashionrental.receipt;

import com.fashionrental.common.exception.ConflictException;
import com.fashionrental.common.exception.ResourceNotFoundException;
import com.fashionrental.config.AppUser;
import com.fashionrental.config.AppUserRepository;
import com.fashionrental.customer.Customer;
import com.fashionrental.receipt.model.request.CancelReceiptRequest;
import com.fashionrental.receipt.model.response.ReceiptResponse;
import com.fashionrental.support.TestData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
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
class ReceiptCancellationServiceTest {

    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-10-10T10:00:00+05:30");
    private static final Clock FIXED_CLOCK = Clock.fixed(NOW.toInstant(), ZoneId.of("Asia/Kolkata"));

    @Mock private ReceiptRepository receiptRepository;
    @Mock private AppUserRepository appUserRepository;

    private Receipt receipt;
    private AppUser owner;
    private ReceiptCancellationService service;

    @BeforeEach
    void setUp() {
        Customer customer = TestData.customer("Asha", "9812345678");
        OffsetDateTime start = NOW.minusDays(1);
        OffsetDateTime end = NOW.plusDays(2);
        receipt = TestData.receipt(customer, start, end, TestData.receiptLineItem(TestData.item("Sherwani", 300, 1000), 1));

        receipt.setStatus(Receipt.Status.GENERATED);

        owner = new AppUser();
        owner.setUsername("owner");
        owner.setRole(AppUser.Role.OWNER);
        owner.setActive(true);

        service = new ReceiptCancellationService(receiptRepository, appUserRepository, new ReceiptMapper(), FIXED_CLOCK);
    }

    private CancelReceiptRequest request(Receipt.CancellationReason reason, String detail) {
        return new CancelReceiptRequest(reason, detail);
    }

    @Test
    void should_cancel_generated_receipt_and_record_when_who_and_why() {
        when(receiptRepository.findByIdForUpdate(receipt.getId())).thenReturn(Optional.of(receipt));
        when(appUserRepository.findByUsernameAndIsActiveTrue("owner")).thenReturn(Optional.of(owner));
        when(receiptRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ReceiptResponse response = service.cancelReceipt(
                receipt.getId(), request(Receipt.CancellationReason.WRONG_ORDER, null), "owner");

        assertThat(response.status()).isEqualTo("CANCELLED");
        assertThat(receipt.getStatus()).isEqualTo(Receipt.Status.CANCELLED);
        assertThat(receipt.getCancelledAt()).isEqualTo(NOW);
        assertThat(receipt.getCancelledBy()).isEqualTo(owner);
        assertThat(receipt.getCancellationReason()).isEqualTo(Receipt.CancellationReason.WRONG_ORDER);
        assertThat(receipt.getCancellationReasonDetail()).isNull();
    }

    @Test
    void should_store_trimmed_detail_when_reason_is_other() {
        when(receiptRepository.findByIdForUpdate(receipt.getId())).thenReturn(Optional.of(receipt));
        when(appUserRepository.findByUsernameAndIsActiveTrue("owner")).thenReturn(Optional.of(owner));
        when(receiptRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.cancelReceipt(receipt.getId(), request(Receipt.CancellationReason.OTHER, "  customer moved cities  "), "owner");

        assertThat(receipt.getCancellationReasonDetail()).isEqualTo("customer moved cities");
    }

    @Test
    void should_discard_detail_when_reason_is_not_other() {
        when(receiptRepository.findByIdForUpdate(receipt.getId())).thenReturn(Optional.of(receipt));
        when(appUserRepository.findByUsernameAndIsActiveTrue("owner")).thenReturn(Optional.of(owner));
        when(receiptRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.cancelReceipt(receipt.getId(), request(Receipt.CancellationReason.WRONG_ORDER, "ignored text"), "owner");

        assertThat(receipt.getCancellationReasonDetail()).isNull();
    }

    @Test
    void should_refund_the_grand_total_net_of_discount_plus_deposit() {
        // grand_total is rent net of discount plus deposit (receipts_grand_total_check);
        // the refund shown to the owner and counted in reporting is this same figure.
        receipt.setDiscountAmount(60);
        receipt.setGrandTotal(receipt.getTotalRent() - receipt.getDiscountAmount() + receipt.getTotalDeposit());
        int expectedRefund = receipt.getGrandTotal();
        when(receiptRepository.findByIdForUpdate(receipt.getId())).thenReturn(Optional.of(receipt));
        when(appUserRepository.findByUsernameAndIsActiveTrue("owner")).thenReturn(Optional.of(owner));
        when(receiptRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ReceiptResponse response = service.cancelReceipt(
                receipt.getId(), request(Receipt.CancellationReason.WRONG_ORDER, null), "owner");

        assertThat(response.grandTotal()).isEqualTo(expectedRefund);
        assertThat(response.grandTotal())
                .isEqualTo(receipt.getTotalRent() - receipt.getDiscountAmount() + receipt.getTotalDeposit());
    }

    @Test
    void should_lock_the_receipt_row_for_update_rather_than_reading_it_plainly() {
        // The lock is what closes the cancel-vs-return race (see ReceiptRepository.findByIdForUpdate);
        // a regression back to a plain findById would silently reopen that race.
        when(receiptRepository.findByIdForUpdate(receipt.getId())).thenReturn(Optional.of(receipt));
        when(appUserRepository.findByUsernameAndIsActiveTrue("owner")).thenReturn(Optional.of(owner));
        when(receiptRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.cancelReceipt(receipt.getId(), request(Receipt.CancellationReason.WRONG_ORDER, null), "owner");

        verify(receiptRepository).findByIdForUpdate(receipt.getId());
        verify(receiptRepository, never()).findById(any());
    }

    @Test
    void should_reject_when_receipt_already_cancelled() {
        receipt.cancel(owner, NOW.minusDays(1), Receipt.CancellationReason.WRONG_ORDER, null);
        when(receiptRepository.findByIdForUpdate(receipt.getId())).thenReturn(Optional.of(receipt));

        assertThatThrownBy(() -> service.cancelReceipt(
                receipt.getId(), request(Receipt.CancellationReason.WRONG_ORDER, null), "owner"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("is CANCELLED and can no longer be cancelled");

        verify(receiptRepository, never()).save(any());
    }

    @Test
    void should_reject_when_receipt_returned() {
        receipt.setStatus(Receipt.Status.RETURNED);
        when(receiptRepository.findByIdForUpdate(receipt.getId())).thenReturn(Optional.of(receipt));

        assertThatThrownBy(() -> service.cancelReceipt(
                receipt.getId(), request(Receipt.CancellationReason.WRONG_ORDER, null), "owner"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("is RETURNED and can no longer be cancelled");

        verify(receiptRepository, never()).save(any());
    }

    @Test
    void should_reject_when_receipt_already_given_to_customer() {
        receipt.markGiven(NOW.minusHours(2));
        when(receiptRepository.findByIdForUpdate(receipt.getId())).thenReturn(Optional.of(receipt));

        assertThatThrownBy(() -> service.cancelReceipt(
                receipt.getId(), request(Receipt.CancellationReason.WRONG_ORDER, null), "owner"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("is GIVEN and can no longer be cancelled");

        verify(receiptRepository, never()).save(any());
    }

    @Test
    void should_allow_cancelling_generated_receipt_ending_within_12_hours() {
        receipt.setEndDatetime(NOW.plusHours(1));
        when(receiptRepository.findByIdForUpdate(receipt.getId())).thenReturn(Optional.of(receipt));
        when(appUserRepository.findByUsernameAndIsActiveTrue("owner")).thenReturn(Optional.of(owner));
        when(receiptRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ReceiptResponse response = service.cancelReceipt(
                receipt.getId(), request(Receipt.CancellationReason.WRONG_ORDER, null), "owner");

        assertThat(response.status()).isEqualTo("CANCELLED");
    }

    @Test
    void should_allow_cancelling_generated_receipt_whose_end_has_passed_because_customer_never_came() {
        receipt.setEndDatetime(NOW.minusHours(5));
        when(receiptRepository.findByIdForUpdate(receipt.getId())).thenReturn(Optional.of(receipt));
        when(appUserRepository.findByUsernameAndIsActiveTrue("owner")).thenReturn(Optional.of(owner));
        when(receiptRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ReceiptResponse response = service.cancelReceipt(
                receipt.getId(), request(Receipt.CancellationReason.CUSTOMER_DOES_NOT_WANT, null), "owner");

        assertThat(response.status()).isEqualTo("CANCELLED");
    }

    @Test
    void should_throw_not_found_for_unknown_receipt() {
        UUID missingId = UUID.randomUUID();
        when(receiptRepository.findByIdForUpdate(missingId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.cancelReceipt(
                missingId, request(Receipt.CancellationReason.WRONG_ORDER, null), "owner"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Receipt not found");
    }

    @Test
    void should_throw_when_acting_user_not_found_or_inactive() {
        when(receiptRepository.findByIdForUpdate(receipt.getId())).thenReturn(Optional.of(receipt));
        when(appUserRepository.findByUsernameAndIsActiveTrue("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.cancelReceipt(
                receipt.getId(), request(Receipt.CancellationReason.WRONG_ORDER, null), "ghost"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Authenticated user not found");

        verify(receiptRepository, never()).save(any());
    }

    @Test
    void should_not_save_anything_when_ineligible() {
        receipt.setStatus(Receipt.Status.RETURNED);
        when(receiptRepository.findByIdForUpdate(receipt.getId())).thenReturn(Optional.of(receipt));

        assertThatThrownBy(() -> service.cancelReceipt(
                receipt.getId(), request(Receipt.CancellationReason.WRONG_ORDER, null), "owner"));

        verify(receiptRepository, never()).save(any());
    }
}
