package com.fashionrental.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class ClockConfig {

    // Injectable so time-dependent logic (e.g. coupon validity windows) can be tested
    // deterministically instead of sleeping or mocking OffsetDateTime.now().
    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
