package com.tellerchannel.coremock;

import org.springframework.http.HttpStatus;

public class AccountNotFoundException extends CoreBusinessException {
    public AccountNotFoundException(String accountNo) {
        super(HttpStatus.NOT_FOUND, "ACCT_NOT_FOUND", "Account not found: " + accountNo);
    }
}
