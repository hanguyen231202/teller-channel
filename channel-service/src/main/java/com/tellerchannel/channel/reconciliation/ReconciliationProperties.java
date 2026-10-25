package com.tellerchannel.channel.reconciliation;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties("teller.reconciliation")
public record ReconciliationProperties(
        boolean enabled,
        @NotNull Duration minAge,
        @Min(1) int batchSize,
        @Min(1) int maxAttempts) {
}
