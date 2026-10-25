package com.tellerchannel.channel.transaction;

public enum TransactionType {
    /** Nộp tiền mặt: core ghi Có (CREDIT) tài khoản khách. */
    DEPOSIT,
    /** Rút tiền mặt: core ghi Nợ (DEBIT) tài khoản khách. */
    WITHDRAWAL
}
