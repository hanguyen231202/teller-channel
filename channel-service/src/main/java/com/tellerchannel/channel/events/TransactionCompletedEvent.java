package com.tellerchannel.channel.events;

import com.tellerchannel.channel.transaction.TellerTransaction;
import com.tellerchannel.channel.transaction.TransactionStatus;
import com.tellerchannel.channel.transaction.TransactionType;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Sự kiện khi giao dịch kết thúc (SUCCESS / FAILED / REJECTED).
 * Bên nhận (gửi SMS, báo cáo) phải idempotent theo transactionRef, vì Kafka giao "ít nhất một lần".
 */
public record TransactionCompletedEvent(
        String transactionRef,
        TransactionType type,
        String accountNo,
        BigDecimal amount,
        String currency,
        TransactionStatus status,
        String corePostingId,
        String coreErrorCode,
        String makerId,
        String checkerId,
        Instant occurredAt) {

    public static TransactionCompletedEvent of(TellerTransaction t) {
        return new TransactionCompletedEvent(t.getTransactionRef(), t.getType(), t.getAccountNo(), t.getAmount(),
                t.getCurrency(), t.getStatus(), t.getCorePostingId(), t.getCoreErrorCode(), t.getMakerId(),
                t.getCheckerId(), t.getUpdatedAt());
    }
}
