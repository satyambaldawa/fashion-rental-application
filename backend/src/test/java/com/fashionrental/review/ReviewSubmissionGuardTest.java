package com.fashionrental.review;

import com.fashionrental.common.exception.RateLimitExceededException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReviewSubmissionGuardTest {

    private static final String IP = "203.0.113.7";
    private static final String PHONE = "9876543210";
    private static final Instant NOW = Instant.parse("2026-09-21T10:00:00Z");

    @Mock
    private ReviewRepository reviewRepository;

    private ReviewSubmissionGuard guard;

    @BeforeEach
    void setUp() {
        guard = new ReviewSubmissionGuard(reviewRepository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void should_allow_submission_when_no_prior_reviews_exist() {
        when(reviewRepository.countBySubmitterIpAndCreatedAtAfter(eq(IP), any())).thenReturn(0L);
        when(reviewRepository.countByPhoneAndCreatedAtAfter(eq(PHONE), any())).thenReturn(0L);

        assertThatCode(() -> guard.checkSubmissionAllowed(IP, PHONE)).doesNotThrowAnyException();
    }

    @Test
    void should_reject_when_ip_has_three_reviews_within_the_last_hour() {
        when(reviewRepository.countBySubmitterIpAndCreatedAtAfter(eq(IP), any())).thenReturn(3L);

        assertThatThrownBy(() -> guard.checkSubmissionAllowed(IP, PHONE))
                .isInstanceOf(RateLimitExceededException.class)
                .hasMessageContaining("try again in an hour");
    }

    @Test
    void should_count_the_ip_window_from_exactly_one_hour_before_now() {
        OffsetDateTime expectedWindowStart = OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC).minusHours(1);
        when(reviewRepository.countBySubmitterIpAndCreatedAtAfter(IP, expectedWindowStart)).thenReturn(0L);
        when(reviewRepository.countByPhoneAndCreatedAtAfter(eq(PHONE), any())).thenReturn(0L);

        assertThatCode(() -> guard.checkSubmissionAllowed(IP, PHONE)).doesNotThrowAnyException();
    }

    @Test
    void should_reject_when_phone_has_five_reviews_within_twenty_four_hours() {
        when(reviewRepository.countBySubmitterIpAndCreatedAtAfter(eq(IP), any())).thenReturn(0L);
        when(reviewRepository.countByPhoneAndCreatedAtAfter(eq(PHONE), any())).thenReturn(5L);

        assertThatThrownBy(() -> guard.checkSubmissionAllowed(IP, PHONE))
                .isInstanceOf(RateLimitExceededException.class)
                .hasMessageContaining("mobile number");
    }

    @Test
    void should_report_the_ip_limit_when_both_limits_are_exceeded() {
        when(reviewRepository.countBySubmitterIpAndCreatedAtAfter(eq(IP), any())).thenReturn(9L);

        assertThatThrownBy(() -> guard.checkSubmissionAllowed(IP, PHONE))
                .hasMessageContaining("try again in an hour");
    }
}
