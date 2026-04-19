package com.fashionrental.common.util;

import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;

@Component
public class DateTimeUtil {

    public int calculateRentalDays(OffsetDateTime start, OffsetDateTime end) {
        long seconds = ChronoUnit.SECONDS.between(start, end);
        int days = (int) (seconds / 86400);
        return Math.max(days, 1);
    }

    public double calculateOverdueHours(OffsetDateTime endDatetime, OffsetDateTime returnDatetime) {
        long seconds = ChronoUnit.SECONDS.between(endDatetime, returnDatetime);
        return seconds / 3600.0;
    }
}
