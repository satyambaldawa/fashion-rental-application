package com.fashionrental.receipt;

import com.fashionrental.common.numbering.DailyDocumentCounter;
import com.fashionrental.common.numbering.DocumentSeries;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Service
public class ReceiptNumberService {

    private static final ZoneId SHOP_ZONE = ZoneId.of("Asia/Kolkata");
    private static final String PREFIX = "R-";

    private final DailyDocumentCounter counter;
    private final Clock clock;

    public ReceiptNumberService(DailyDocumentCounter counter, Clock clock) {
        this.counter = counter;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public String generateReceiptNumber() {
        LocalDate today = LocalDate.now(clock.withZone(SHOP_ZONE));
        int sequence = counter.nextValue(DocumentSeries.RECEIPT, today);
        return PREFIX + today.format(DateTimeFormatter.BASIC_ISO_DATE) + "-" + String.format("%03d", sequence);
    }
}
