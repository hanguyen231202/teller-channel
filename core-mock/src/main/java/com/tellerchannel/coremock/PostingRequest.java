package com.tellerchannel.coremock;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Lệnh hạch toán kênh gửi sang core.
 * referenceNo do kênh sinh, duy nhất cho mỗi giao dịch: core dùng nó để chống hạch toán trùng.
 */
public record PostingRequest(
        @NotBlank @Size(max = 30) String referenceNo,
        @NotBlank @Pattern(regexp = AccountController.ACCOUNT_NO_PATTERN) String accountNo,
        @NotNull PostingDirection direction,
        @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 17, fraction = 2) BigDecimal amount,
        @NotBlank @Pattern(regexp = "[A-Z]{3}") String currency,
        @Size(max = 255) String description) {
}
