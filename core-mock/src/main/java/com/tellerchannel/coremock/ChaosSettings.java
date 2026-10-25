package com.tellerchannel.coremock;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Cấu hình "làm hư" core-mock để luyện xử lý sự cố ở phía kênh.
 *
 * @param postingDelayMs     ngủ bao lâu TRƯỚC khi hạch toán. Lớn hơn read timeout của kênh thì
 *                           kênh bỏ cuộc (UNKNOWN) nhưng core VẪN hạch toán sau đó: đúng tình huống
 *                           "timeout mà tiền đã trừ" cần đối soát.
 * @param postingFailureRate tỉ lệ (0..1) trả 503 CORE_UNAVAILABLE cho lệnh hạch toán, trước khi xử lý.
 * @param inquiryFailureRate tỉ lệ (0..1) trả 503 cho API tra cứu (tài khoản, bút toán).
 */
@ConfigurationProperties("core-mock.chaos")
public record ChaosSettings(
        @DefaultValue("0") @Min(0) @Max(120_000) long postingDelayMs,
        @DefaultValue("0") @DecimalMin("0.0") @DecimalMax("1.0") double postingFailureRate,
        @DefaultValue("0") @DecimalMin("0.0") @DecimalMax("1.0") double inquiryFailureRate) {

    public static ChaosSettings none() {
        return new ChaosSettings(0, 0, 0);
    }
}
