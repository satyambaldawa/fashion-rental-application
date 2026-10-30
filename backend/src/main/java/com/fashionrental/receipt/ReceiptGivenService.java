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
        Receipt receipt = findReceiptLockedForUpdate(receiptId);

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
        Receipt receipt = findReceiptLockedForUpdate(receiptId);

        if (receipt.getStatus() == Receipt.Status.GENERATED) {
            return receiptMapper.toReceiptResponse(receipt);
        }

        OffsetDateTime now = OffsetDateTime.now(clock);
        assertGivenUndoable(receipt, now);

        receipt.revertGiven(loadAuthenticatedActiveUser(actingUsername), now);
        return receiptMapper.toReceiptResponse(receiptRepository.save(receipt));
    }

    // Receipt has no @Version, so without this row lock a give/undo could overwrite a concurrent
    // cancel or return with stale status and audit fields. The loser of the race re-reads the
    // committed status and hits a guard instead.
    private Receipt findReceiptLockedForUpdate(UUID receiptId) {
        return receiptRepository.findByIdForUpdate(receiptId)
                .orElseThrow(() -> new ResourceNotFoundException("Receipt not found: " + receiptId));
    }

    private AppUser loadAuthenticatedActiveUser(String username) {
        return appUserRepository.findByUsernameAndIsActiveTrue(username)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + username));
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
