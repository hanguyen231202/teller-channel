package com.tellerchannel.coremock;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AccountService {

    private final Map<String, Account> accounts = new ConcurrentHashMap<>();

    public AccountService() {
        Account accHaNguyen = new Account("23122002", "NGUYEN THANH HA", "VND", new BigDecimal("1500000"), AccountStatus.ACTIVE);
        Account accKieuOanh = new Account("17012007", "NGUYEN KIEU OANH", "VND", new BigDecimal("2000000"), AccountStatus.ACTIVE);
        Account accBig = new Account("12345678", "PHAM THI MAI", "VND", new BigDecimal("500000000"), AccountStatus.ACTIVE);
        Account accBlocked = new Account("88889999", "TRAN VAN KHOA", "VND", new BigDecimal("10000000"), AccountStatus.BLOCKED);

        accounts.put(accHaNguyen.accountNo(), accHaNguyen);
        accounts.put(accKieuOanh.accountNo(), accKieuOanh);
        accounts.put(accBig.accountNo(), accBig);
        accounts.put(accBlocked.accountNo(), accBlocked);
    }

    public Account findByAccountNo(String accountNo) {
        Account acc = accounts.get(accountNo);

        if (acc == null) throw new AccountNotFoundException(accountNo);
        return acc;
    }

    /**
     * Chỉ PostingService gọi (package-private), và luôn gọi bên trong vùng đã khóa,
     * nên "đọc số dư → kiểm tra → ghi số dư" không bị thread khác chen vào giữa.
     */
    void updateBalance(String accountNo, BigDecimal newBalance) {
        accounts.computeIfPresent(accountNo, (no, acc) -> acc.withBalance(newBalance));
    }
}
