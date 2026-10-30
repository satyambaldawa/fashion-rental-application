package com.fashionrental.receipt;

import com.fashionrental.common.exception.ConflictException;
import com.fashionrental.common.exception.ResourceNotFoundException;
import com.fashionrental.config.AppUser;
import com.fashionrental.config.AppUserRepository;
import com.fashionrental.receipt.model.response.ReceiptResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Handing the dress over is a state change distinct from checkout, cancelling and returning.
 * Undoing a hand-over is its inverse and lives here too, rather than in its own service.
 */
@Service
public class ReceiptGivenService {

    private final ReceiptRepository receiptRepository;
    private final AppUserRepository appUserRepository;
    private final ReceiptMapper receiptMapper;
    private final Clock clock;

    public ReceiptGivenService(
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
    public ReceiptResponse markGiven(UUID receiptId) {
        // Row lock: Receipt has no @Version, so an unlocked give could overwrite a concurrent
        // cancellation's status and audit fields with stale values.
        Receipt receipt = receiptRepository.findByIdForUpdate(receiptId)
                .orElseThrow(() -> new ResourceNotFoundException("Receipt not found: " + receiptId));

        if (receipt.getStatus() == Receipt.Status.GIVEN) {
            return receiptMapper.toReceiptResponse(receipt);
        }
        if (receipt.getStatus() != Receipt.Status.GENERATED) {
            throw new ConflictException("Receipt " + receipt.getReceiptNumber() + " is " + receipt.getStatus()
                    + " and cannot be marked as given");
        }

        receipt.markGiven(OffsetDateTime.now(clock));
        return receiptMapper.toReceiptResponse(receiptRepository.save(receipt));
    }

    @Transactional
    public ReceiptResponse undoGiven(UUID receiptId, String actingUsername) {
        // Same row lock as markGiven/cancel/return: whoever gets here first holds the row until
        // commit, so the loser re-reads the post-commit status and hits a guard below instead of
        // overwriting it (#179).
        Receipt receipt = receiptRepository.findByIdForUpdate(receiptId)
                .orElseThrow(() -> new ResourceNotFoundException("Receipt not found: " + receiptId));

        if (receipt.getStatus() == Receipt.Status.GENERATED) {
            return receiptMapper.toReceiptResponse(receipt);
        }

        OffsetDateTime now = OffsetDateTime.now(clock);
        assertGivenUndoable(receipt, now);

        // The JWT filter loads only active users (UserDetailsConfig), so a miss here is an invariant breach.
        AppUser actingUser = appUserRepository.findByUsernameAndIsActiveTrue(actingUsername)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + actingUsername));

        receipt.revertGiven(actingUser, now);
        return receiptMapper.toReceiptResponse(receiptRepository.save(receipt));
    }

    private void assertGivenUndoable(Receipt receipt, OffsetDateTime now) {
        if (receipt.getStatus() != Receipt.Status.GIVEN) {
            throw new ConflictException("Receipt " + receipt.getReceiptNumber() + " is " + receipt.getStatus()
                    + " and its hand-over can no longer be undone");
        }
        if (!receipt.getStartDatetime().isAfter(now)) {
            throw new ConflictException("Receipt " + receipt.getReceiptNumber()
                    + " has passed its booked start time and its hand-over can no longer be undone");
        }
    }
}
