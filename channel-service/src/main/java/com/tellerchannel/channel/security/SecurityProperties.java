package com.tellerchannel.channel.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/** @Validated: thiếu khóa hoặc khóa quá ngắn thì app không khởi động được (fail fast). */
@Validated
@ConfigurationProperties("teller.security")
public record SecurityProperties(
        @NotBlank @Size(min = 32) String jwtSecret,
        Duration tokenTtl,
        @NotBlank String issuer,
        @NotBlank String demoPassword) {
}
