package com.tellerchannel.channel.account;

import com.tellerchannel.channel.core.CoreAccount;
import com.tellerchannel.channel.core.CoreBankingAdapter;
import jakarta.validation.constraints.Pattern;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Tra cứu tài khoản: hỏi thẳng core mỗi lần, kênh không lưu số dư. */
@RestController
@RequestMapping("/api/accounts")
public class AccountController {

    private final CoreBankingAdapter coreBankingAdapter;

    public AccountController(CoreBankingAdapter coreBankingAdapter) {
        this.coreBankingAdapter = coreBankingAdapter;
    }

    @GetMapping("/{accountNo}")
    public CoreAccount get(@PathVariable @Pattern(regexp = "\\d{8,14}") String accountNo) {
        return coreBankingAdapter.getAccount(accountNo);
    }
}
