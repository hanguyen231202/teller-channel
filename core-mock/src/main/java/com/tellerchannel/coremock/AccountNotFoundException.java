package com.tellerchannel.coremock;

public class AccountNotFoundException extends RuntimeException {
    public AccountNotFoundException(String accountNo) {
        super("Account not found: " + accountNo);
    }
}
