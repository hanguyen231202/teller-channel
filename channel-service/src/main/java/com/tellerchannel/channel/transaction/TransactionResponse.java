package com.tellerchannel.channel.transaction;

import java.math.BigDecimal;
import java.time.Instant;

/** DTO trả ra API. Không trả thẳng entity để không lộ cột nội bộ (version, idempotencyKey...). */
public record TransactionResponse(
        Long id,
        String transactionRef,
        TransactionType type,
        String accountNo,
        BigDecimal amount,
        String currency,
        String description,
        TransactionStatus status,
        String makerId,
        String checkerId,
        String rejectReason,
        String corePostingId,
        String coreErrorCode,
        String coreErrorMessage,
        int reconcileAttempts,
        Instant createdAt,
        Instant updatedAt,
        Instant completedAt) {

    public static TransactionResponse from(TellerTransaction t) {
        return new TransactionResponse(t.getId(), t.getTransactionRef(), t.getType(), t.getAccountNo(),
                t.getAmount(), t.getCurrency(), t.getDescription(), t.getStatus(), t.getMakerId(),
                t.getCheckerId(), t.getRejectReason(), t.getCorePostingId(), t.getCoreErrorCode(),
                t.getCoreErrorMessage(), t.getReconcileAttempts(), t.getCreatedAt(), t.getUpdatedAt(),
                t.getCompletedAt());
    }
}
