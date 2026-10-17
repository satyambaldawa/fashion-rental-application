package com.fashionrental.receipt;

import com.fashionrental.common.exception.ConflictException;
import com.fashionrental.common.exception.ResourceNotFoundException;
import com.fashionrental.receipt.model.response.ReceiptResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Handing the dress over is a state change distinct from checkout, cancelling and returning.
 */
@Service
public class ReceiptGivenService {

    private final ReceiptRepository receiptRepository;
    private final ReceiptMapper receiptMapper;
    private final Clock clock;

    public ReceiptGivenService(ReceiptRepository receiptRepository, ReceiptMapper receiptMapper, Clock clock) {
        this.receiptRepository = receiptRepository;
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
}
