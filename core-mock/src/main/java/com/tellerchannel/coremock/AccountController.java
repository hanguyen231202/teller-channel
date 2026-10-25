package com.tellerchannel.coremock;

import jakarta.validation.constraints.Pattern;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/core/accounts")
public class AccountController {

    /** Số tài khoản: 8–14 chữ số. Sai định dạng thì trả 400, không cần tra map. */
    static final String ACCOUNT_NO_PATTERN = "\\d{8,14}";

    private final AccountService accountService;
    private final ChaosService chaosService;

    public AccountController(AccountService accountService, ChaosService chaosService) {
        this.accountService = accountService;
        this.chaosService = chaosService;
    }

    @GetMapping("/{accountNo}")
    public Account getAccount(@PathVariable @Pattern(regexp = ACCOUNT_NO_PATTERN) String accountNo) {
        chaosService.beforeInquiry();
        return accountService.findByAccountNo(accountNo);
    }
}
