package com.tellerchannel.coremock;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class AccountServiceTest {

    private final AccountService service = new AccountService();

    @Test
    void findByAccountNo_existing_returnsAccount() {
        String accountNo = "23122002";
        Account account = service.findByAccountNo(accountNo);

        assertEquals("23122002", account.accountNo());
        assertEquals(new BigDecimal("1500000"), account.balance());
    }

    @Test
    void findByAccountNo_unknown_throwsNotFound() {
        assertThrows(AccountNotFoundException.class, () -> service.findByAccountNo("9999999999"));
    }
}