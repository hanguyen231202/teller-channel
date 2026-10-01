package com.tellerchannel.coremock;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AccountService {

    private final Map<String, Account> accounts = new ConcurrentHashMap<>();

    public AccountService() {
        Account accHaNguyen = new Account("23122002", "NGUYEN THANH HA", "VND", new BigDecimal("1500000"));
        Account accKieuOanh = new Account("17012007", "NGUYEN KIEU OANH", "VND", new BigDecimal("2000000"));

        accounts.put(accHaNguyen.accountNo(), accHaNguyen);
        accounts.put(accKieuOanh.accountNo(), accKieuOanh);
    }

    public Account findByAccountNo(String accountNo) {
        Account acc = accounts.get(accountNo);

        if (acc == null) throw new AccountNotFoundException(accountNo);
        return acc;
    }
}
