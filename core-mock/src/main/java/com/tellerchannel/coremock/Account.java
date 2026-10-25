package com.tellerchannel.coremock;

import java.math.BigDecimal;

public record Account(String accountNo, String accountName, String currency, BigDecimal balance, AccountStatus status) {

    /** record bất biến: đổi số dư nghĩa là tạo bản ghi mới. */
    public Account withBalance(BigDecimal newBalance) {
        return new Account(accountNo, accountName, currency, newBalance, status);
    }
}
