package com.tellerchannel.channel.transaction;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tellerchannel.channel.core.CoreBankingAdapter;
import com.tellerchannel.channel.core.CorePosting;
import com.tellerchannel.channel.core.PostingOutcome;
import com.tellerchannel.channel.support.AuthHelper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Test qua HTTP: phân quyền theo vai trò, header Idempotency-Key, mã lỗi trả về. */
@SpringBootTest(properties = "teller.reconciliation.enabled=false")
@AutoConfigureMockMvc
class TransactionControllerTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @MockitoBean
    CoreBankingAdapter core;

    private static final String BODY = """
            {"type":"WITHDRAWAL","accountNo":"23122002","amount":500000,"currency":"VND","description":"Rut tien"}
            """;

    private long createAndSubmit(String tellerToken) throws Exception {
        String response = mvc.perform(post("/api/transactions").header("Authorization", tellerToken)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("INITIATED"))
                .andReturn().getResponse().getContentAsString();
        long id = json.readTree(response).get("id").asLong();
        mvc.perform(post("/api/transactions/{id}/submit", id).header("Authorization", tellerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_APPROVAL"));
        return id;
    }

    @Test
    void fullFlow_tellerCreates_supervisorApproves_success() throws Exception {
        when(core.post(any())).thenAnswer(inv -> new PostingOutcome.Posted(new CorePosting("PST9",
                "ref", "23122002", "DEBIT", new BigDecimal("500000"), "VND", BigDecimal.ONE, Instant.now())));
        String gdv = AuthHelper.bearer(mvc, json, "gdv01");
        String ksv = AuthHelper.bearer(mvc, json, "ksv01");
        long id = createAndSubmit(gdv);

        mvc.perform(get("/api/transactions").param("status", "PENDING_APPROVAL").header("Authorization", ksv))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id == " + id + ")]").exists());

        mvc.perform(post("/api/transactions/{id}/approve", id).header("Authorization", ksv))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.makerId").value("gdv01"))
                .andExpect(jsonPath("$.checkerId").value("ksv01"));

        mvc.perform(get("/api/transactions/{id}/audit", id).header("Authorization", ksv))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4));
    }

    @Test
    void tellerCannotApprove_403() throws Exception {
        String gdv = AuthHelper.bearer(mvc, json, "gdv01");
        long id = createAndSubmit(gdv);

        mvc.perform(post("/api/transactions/{id}/approve", id).header("Authorization", AuthHelper.bearer(mvc, json, "gdv02")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void supervisorCannotCreate_403() throws Exception {
        mvc.perform(post("/api/transactions").header("Authorization", AuthHelper.bearer(mvc, json, "ksv01"))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    void missingIdempotencyKey_400() throws Exception {
        mvc.perform(post("/api/transactions").header("Authorization", AuthHelper.bearer(mvc, json, "gdv01"))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MISSING_IDEMPOTENCY_KEY"));
    }

    @Test
    void replayWithSameKey_returns200AndSameId() throws Exception {
        String gdv = AuthHelper.bearer(mvc, json, "gdv01");
        String key = UUID.randomUUID().toString();
        String first = mvc.perform(post("/api/transactions").header("Authorization", gdv).header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();

        mvc.perform(post("/api/transactions").header("Authorization", gdv).header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(json.readTree(first).get("id").asLong()));
    }

    @Test
    void negativeAmount_400ValidationError() throws Exception {
        mvc.perform(post("/api/transactions").header("Authorization", AuthHelper.bearer(mvc, json, "gdv01"))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content(BODY.replace("500000", "-5")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void unknownTransaction_404() throws Exception {
        mvc.perform(get("/api/transactions/{id}", 999999).header("Authorization", AuthHelper.bearer(mvc, json, "ksv01")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TXN_NOT_FOUND"));
    }
}
