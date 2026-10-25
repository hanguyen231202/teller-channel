package com.tellerchannel.channel.common;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/** Tiêm Clock thay vì gọi Instant.now() trực tiếp, để test cố định được thời gian. */
@Configuration
public class ClockConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
