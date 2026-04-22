package com.fashionrental.receipt;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Service
public class ReceiptNumberService {

    private final ReceiptRepository receiptRepository;

    public ReceiptNumberService(ReceiptRepository receiptRepository) {
        this.receiptRepository = receiptRepository;
    }

    @Transactional
    public String generateReceiptNumber() {
        String today = LocalDate.now(ZoneId.of("Asia/Kolkata")).format(DateTimeFormatter.BASIC_ISO_DATE);
        String prefix = "R-" + today + "-";
        long count = receiptRepository.countByReceiptNumberStartingWith(prefix);
        return prefix + String.format("%03d", count + 1);
    }
}
