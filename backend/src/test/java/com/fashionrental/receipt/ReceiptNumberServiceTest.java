package com.fashionrental.receipt;

import com.fashionrental.common.numbering.DailyDocumentCounter;
import com.fashionrental.common.numbering.DocumentSeries;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReceiptNumberServiceTest {

    private static final LocalDate IST_DAY = LocalDate.of(2026, 11, 3);

    private final DailyDocumentCounter counter = mock(DailyDocumentCounter.class);

    private ReceiptNumberService serviceAt(String utcInstant) {
        Clock clock = Clock.fixed(Instant.parse(utcInstant), ZoneOffset.UTC);
        return new ReceiptNumberService(counter, clock);
    }

    @Test
    void should_zero_pad_the_sequence_to_three_digits() {
        when(counter.nextValue(DocumentSeries.RECEIPT, IST_DAY)).thenReturn(7);

        String number = serviceAt("2026-11-03T06:00:00Z").generateReceiptNumber();

        assertThat(number).isEqualTo("R-20261103-007");
    }

    @Test
    void should_grow_to_four_digits_after_999() {
        when(counter.nextValue(DocumentSeries.RECEIPT, IST_DAY)).thenReturn(1000);

        String number = serviceAt("2026-11-03T06:00:00Z").generateReceiptNumber();

        assertThat(number).isEqualTo("R-20261103-1000");
    }

    @Test
    void should_use_the_indian_calendar_date_when_utc_is_still_the_previous_day() {
        LocalDate nextIstDay = LocalDate.of(2026, 11, 4);
        when(counter.nextValue(DocumentSeries.RECEIPT, nextIstDay)).thenReturn(1);

        String number = serviceAt("2026-11-03T19:00:00Z").generateReceiptNumber();

        assertThat(number).isEqualTo("R-20261104-001");
    }
}
