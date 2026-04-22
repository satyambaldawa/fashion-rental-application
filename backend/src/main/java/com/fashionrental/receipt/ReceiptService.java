package com.fashionrental.receipt;

import com.fashionrental.common.exception.ResourceNotFoundException;
import com.fashionrental.common.util.DateTimeUtil;
import com.fashionrental.receipt.model.response.ReceiptResponse;
import com.fashionrental.receipt.model.response.ReceiptSummaryResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class ReceiptService {

    private final ReceiptRepository receiptRepository;
    private final DateTimeUtil dateTimeUtil;
    private final ReceiptMapper receiptMapper;

    public ReceiptService(ReceiptRepository receiptRepository, DateTimeUtil dateTimeUtil, ReceiptMapper receiptMapper) {
        this.receiptRepository = receiptRepository;
        this.dateTimeUtil = dateTimeUtil;
        this.receiptMapper = receiptMapper;
    }

    @Transactional(readOnly = true)
    public List<ReceiptSummaryResponse> listReceipts(Receipt.Status status, Boolean overdue) {
        OffsetDateTime now = OffsetDateTime.now();

        List<Receipt> receipts;
        if (Boolean.TRUE.equals(overdue)) {
            receipts = receiptRepository.findByStatusAndEndDatetimeBeforeOrderByEndDatetimeAsc(Receipt.Status.GIVEN, now);
        } else if (status != null) {
            receipts = receiptRepository.findByStatusOrderByEndDatetimeAsc(status);
        } else {
            receipts = receiptRepository.findByStatusOrderByEndDatetimeAsc(Receipt.Status.GIVEN);
        }

        return receipts.stream()
                .map(r -> toSummaryResponse(r, now))
                .toList();
    }

    @Transactional(readOnly = true)
    public ReceiptResponse getReceipt(UUID id) {
        Receipt receipt = receiptRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Receipt not found: " + id));
        return receiptMapper.toReceiptResponse(receipt);
    }

    private ReceiptSummaryResponse toSummaryResponse(Receipt receipt, OffsetDateTime now) {
        List<String> itemNames = receipt.getLineItems().stream()
                .map(li -> li.getItem().getName() + " \u00d7" + li.getQuantity())
                .toList();

        boolean isOverdue = receipt.getStatus() == Receipt.Status.GIVEN
                && now.isAfter(receipt.getEndDatetime());

        Double overdueHours = null;
        if (isOverdue) {
            overdueHours = dateTimeUtil.calculateOverdueHours(receipt.getEndDatetime(), now);
        }

        return new ReceiptSummaryResponse(
                receipt.getId(),
                receipt.getReceiptNumber(),
                receipt.getCustomer().getName(),
                receipt.getCustomer().getPhone(),
                itemNames,
                receipt.getStartDatetime(),
                receipt.getEndDatetime(),
                receipt.getRentalDays(),
                receipt.getTotalRent(),
                receipt.getTotalDeposit(),
                receipt.getGrandTotal(),
                receipt.getStatus().name(),
                isOverdue,
                overdueHours
        );
    }

}
