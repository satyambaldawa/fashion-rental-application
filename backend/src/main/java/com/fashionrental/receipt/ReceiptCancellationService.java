package com.fashionrental.receipt;

import com.fashionrental.common.exception.ConflictException;
import com.fashionrental.common.exception.ResourceNotFoundException;
import com.fashionrental.config.AppUser;
import com.fashionrental.config.AppUserRepository;
import com.fashionrental.receipt.model.request.CancelReceiptRequest;
import com.fashionrental.receipt.model.response.ReceiptResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Cancelling is a separate state change from checkout (CheckoutService) and returning
 * (ReturnService), with its own eligibility window and audit trail — kept in its own
 * class per the single-responsibility rule in CLAUDE.md.
 */
@Service
public class ReceiptCancellationService {

    static final Duration CANCELLATION_CUTOFF = Duration.ofHours(12);

    private final ReceiptRepository receiptRepository;
    private final AppUserRepository appUserRepository;
    private final ReceiptMapper receiptMapper;
    private final Clock clock;

    public ReceiptCancellationService(
            ReceiptRepository receiptRepository,
            AppUserRepository appUserRepository,
            ReceiptMapper receiptMapper,
            Clock clock
    ) {
        this.receiptRepository = receiptRepository;
        this.appUserRepository = appUserRepository;
        this.receiptMapper = receiptMapper;
        this.clock = clock;
    }

    @Transactional
    public ReceiptResponse cancelReceipt(UUID receiptId, CancelReceiptRequest request, String actingUsername) {
        Receipt receipt = receiptRepository.findByIdForUpdate(receiptId)
                .orElseThrow(() -> new ResourceNotFoundException("Receipt not found: " + receiptId));

        OffsetDateTime now = OffsetDateTime.now(clock);
        assertCancellable(receipt, now);

        // The JWT filter loads only active users (UserDetailsConfig), so a miss here is an invariant breach.
        AppUser actingUser = appUserRepository.findByUsernameAndIsActiveTrue(actingUsername)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + actingUsername));

        String detail = request.reason() == Receipt.CancellationReason.OTHER
                ? request.reasonDetail().strip()
                : null;

        receipt.cancel(actingUser, now, request.reason(), detail);
        Receipt saved = receiptRepository.save(receipt);

        return receiptMapper.toReceiptResponse(saved);
    }

    private void assertCancellable(Receipt receipt, OffsetDateTime now) {
        String number = receipt.getReceiptNumber();

        if (receipt.getStatus() != Receipt.Status.GIVEN) {
            throw new ConflictException(
                    "Receipt " + number + " is " + receipt.getStatus() + " and can no longer be cancelled");
        }
        if (!receipt.getEndDatetime().isAfter(now)) {
            throw new ConflictException("Receipt " + number + " is overdue and cannot be cancelled");
        }
        if (!receipt.getEndDatetime().isAfter(now.plus(CANCELLATION_CUTOFF))) {
            throw new ConflictException("Receipt " + number + " ends within " + CANCELLATION_CUTOFF.toHours()
                    + " hours and can no longer be cancelled");
        }
    }
}
