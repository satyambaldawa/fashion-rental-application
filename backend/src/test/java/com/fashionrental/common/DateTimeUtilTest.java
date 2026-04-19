package com.fashionrental.common;

import com.fashionrental.common.util.DateTimeUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DateTimeUtilTest {

    private static final ZoneOffset IST = ZoneOffset.ofHoursMinutes(5, 30);

    private DateTimeUtil dateTimeUtil;

    @BeforeEach
    void setUp() {
        dateTimeUtil = new DateTimeUtil();
    }

    @Test
    void should_return_one_day_when_rental_spans_exactly_24_hours() {
        OffsetDateTime start = OffsetDateTime.of(2026, 4, 18, 10, 0, 0, 0, IST);
        OffsetDateTime end = OffsetDateTime.of(2026, 4, 19, 10, 0, 0, 0, IST);

        int days = dateTimeUtil.calculateRentalDays(start, end);

        assertEquals(1, days);
    }

    @Test
    void should_return_three_days_when_rental_spans_72_hours() {
        OffsetDateTime start = OffsetDateTime.of(2026, 4, 18, 10, 0, 0, 0, IST);
        OffsetDateTime end = OffsetDateTime.of(2026, 4, 21, 10, 0, 0, 0, IST);

        int days = dateTimeUtil.calculateRentalDays(start, end);

        assertEquals(3, days);
    }

    @Test
    void should_return_minimum_one_day_when_return_is_same_day() {
        OffsetDateTime start = OffsetDateTime.of(2026, 4, 18, 10, 0, 0, 0, IST);
        OffsetDateTime end = OffsetDateTime.of(2026, 4, 18, 16, 0, 0, 0, IST);

        int days = dateTimeUtil.calculateRentalDays(start, end);

        assertEquals(1, days);
    }

    @Test
    void should_return_zero_overdue_hours_when_returned_exactly_on_time() {
        OffsetDateTime due = OffsetDateTime.of(2026, 4, 18, 10, 0, 0, 0, IST);
        OffsetDateTime returned = OffsetDateTime.of(2026, 4, 18, 10, 0, 0, 0, IST);

        double overdueHours = dateTimeUtil.calculateOverdueHours(due, returned);

        assertEquals(0.0, overdueHours);
    }

    @Test
    void should_return_three_overdue_hours_when_returned_at_1pm_and_due_at_10am() {
        OffsetDateTime due = OffsetDateTime.of(2026, 4, 18, 10, 0, 0, 0, IST);
        OffsetDateTime returned = OffsetDateTime.of(2026, 4, 18, 13, 0, 0, 0, IST);

        double overdueHours = dateTimeUtil.calculateOverdueHours(due, returned);

        assertEquals(3.0, overdueHours);
    }

    @Test
    void should_return_24_overdue_hours_when_returned_one_day_late() {
        OffsetDateTime due = OffsetDateTime.of(2026, 4, 18, 10, 0, 0, 0, IST);
        OffsetDateTime returned = OffsetDateTime.of(2026, 4, 19, 10, 0, 0, 0, IST);

        double overdueHours = dateTimeUtil.calculateOverdueHours(due, returned);

        assertEquals(24.0, overdueHours);
    }
}
