package com.tellerchannel.coremock;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AccountController.class)
class AccountControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    AccountService accountService;

    @Test
    void getAccount_existing_returns200WithBody() throws Exception {
        when(accountService.findByAccountNo("23122002")).thenReturn(new Account("23122002", "NGUYEN THANH HA", "VND", new BigDecimal("1500000")));
        mockMvc.perform(get("/core/accounts/23122002"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountNo").value("23122002"));
    }

    @Test
    void getAccount_unknown_returns404ProblemDetail() throws Exception {
        when(accountService.findByAccountNo("99999999999")).thenThrow(new AccountNotFoundException("99999999999"));
        mockMvc.perform(get("/core/accounts/99999999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ACCT_NOT_FOUND"))
                .andExpect(content().contentType("application/problem+json"));
    }
}