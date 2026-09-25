package com.fashionrental.review;

import com.fashionrental.common.exception.RateLimitExceededException;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.OffsetDateTime;

@Component
public class ReviewSubmissionGuard {

    private static final int MAX_PER_IP_PER_HOUR = 3;
    private static final int MAX_PER_PHONE_PER_DAY = 5;

    private final ReviewRepository reviewRepository;
    private final Clock clock;

    public ReviewSubmissionGuard(ReviewRepository reviewRepository, Clock clock) {
        this.reviewRepository = reviewRepository;
        this.clock = clock;
    }

    public void checkSubmissionAllowed(String submitterIp, String phone) {
        OffsetDateTime now = OffsetDateTime.now(clock);

        if (reviewRepository.countBySubmitterIpAndCreatedAtAfter(submitterIp, now.minusHours(1))
                >= MAX_PER_IP_PER_HOUR) {
            throw new RateLimitExceededException("Too many reviews submitted. Please try again in an hour.");
        }

        if (reviewRepository.countByPhoneAndCreatedAtAfter(phone, now.minusDays(1))
                >= MAX_PER_PHONE_PER_DAY) {
            throw new RateLimitExceededException("This mobile number has reached today's review limit.");
        }
    }
}
