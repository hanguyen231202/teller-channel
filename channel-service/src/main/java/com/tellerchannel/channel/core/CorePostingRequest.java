package com.tellerchannel.channel.core;

import java.math.BigDecimal;

/** Lệnh hạch toán gửi core. referenceNo = transactionRef của kênh, để core chống trùng. */
public record CorePostingRequest(String referenceNo, String accountNo, Direction direction,
                                 BigDecimal amount, String currency, String description) {

    public enum Direction { DEBIT, CREDIT }
}
