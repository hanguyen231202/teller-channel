package com.tellerchannel.channel.e2e;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.tellerchannel.channel.support.AuthHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Toàn bộ channel-service thật (security, DB, adapter HTTP thật) + core giả WireMock.
 * Đây là kịch bản người phỏng vấn hay hỏi nhất: "core không trả lời thì sao?"
 */
@SpringBootTest(properties = {
        "teller.reconciliation.enabled=false",
        "teller.core.connect-timeout=500ms",
        "teller.core.read-timeout=500ms",
        "teller.core.retry-backoff=10ms",
        "teller.reconciliation.min-age=5s"
})
@AutoConfigureMockMvc
class TellerFlowEndToEndTest {

    @RegisterExtension
    static WireMockExtension core = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @DynamicPropertySource
    static void coreUrl(DynamicPropertyRegistry registry) {
        registry.add("teller.core.base-url", core::baseUrl);
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    private String gdv;
    private String ksv;

    @BeforeEach
    void login() throws Exception {
        gdv = AuthHelper.bearer(mvc, json, "gdv01");
        ksv = AuthHelper.bearer(mvc, json, "ksv01");
    }

    private String createAndSubmit(String body) throws Exception {
        String response = mvc.perform(post("/api/transactions").header("Authorization", gdv)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String id = json.readTree(response).get("id").asText();
        mvc.perform(post("/api/transactions/" + id + "/submit").header("Authorization", gdv))
                .andExpect(status().isOk());
        return id;
    }

    private String refOf(String id) throws Exception {
        String response = mvc.perform(get("/api/transactions/" + id).header("Authorization", ksv))
                .andReturn().getResponse().getContentAsString();
        return json.readTree(response).get("transactionRef").asText();
    }

    @Test
    void coreTimeout_becomesUnknown_thenReconciliationResolvesSuccess_withoutResending() throws Exception {
        String id = createAndSubmit("""
                {"type":"WITHDRAWAL","accountNo":"23122002","amount":500000,"currency":"VND"}""");
        String ref = refOf(id);
        // Core xử lý chậm 1.5s, kênh chỉ chờ 0.5s
        core.stubFor(postingEndpoint().willReturn(aResponse().withStatus(201).withFixedDelay(1500)
                .withHeader("Content-Type", "application/json").withBody(posting(ref))));

        mvc.perform(post("/api/transactions/" + id + "/approve").header("Authorization", ksv))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UNKNOWN"))
                .andExpect(jsonPath("$.coreErrorCode").value("NO_RESPONSE"));

        // Core thực ra đã hạch toán. KSV bấm đối soát: kênh HỎI core (GET), không gửi lại lệnh.
        core.stubFor(WireMock.get("/core/postings/" + ref)
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody(posting(ref))));

        mvc.perform(post("/api/transactions/" + id + "/reconcile").header("Authorization", ksv))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.corePostingId").value("PST00000077"));

        core.verify(1, postRequestedFor(urlEqualTo("/core/postings"))
                .withRequestBody(matchingJsonPath("$.referenceNo", equalTo(ref))));
    }

    @Test
    void insufficientFunds_isFailedWithCoreCode() throws Exception {
        String id = createAndSubmit("""
                {"type":"WITHDRAWAL","accountNo":"23122002","amount":99000000,"currency":"VND"}""");
        core.stubFor(postingEndpoint().willReturn(aResponse().withStatus(422)
                .withHeader("Content-Type", "application/problem+json")
                .withBody("{\"status\":422,\"detail\":\"Insufficient funds\",\"code\":\"INSUFFICIENT_FUNDS\"}")));

        mvc.perform(post("/api/transactions/" + id + "/approve").header("Authorization", ksv))
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.coreErrorCode").value("INSUFFICIENT_FUNDS"));
    }

    @Test
    void accountInquiry_readsBalanceFromCore() throws Exception {
        core.stubFor(WireMock.get("/core/accounts/23122002")
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"accountNo":"23122002","accountName":"NGUYEN THANH HA","currency":"VND",
                                 "balance":1500000,"status":"ACTIVE"}""")));

        mvc.perform(get("/api/accounts/23122002").header("Authorization", gdv))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(1500000));
    }

    @Test
    void accountInquiry_unknownAccount_passesCoreCode() throws Exception {
        core.stubFor(WireMock.get("/core/accounts/99999999")
                .willReturn(aResponse().withStatus(404).withHeader("Content-Type", "application/problem+json")
                        .withBody("{\"status\":404,\"detail\":\"Account not found\",\"code\":\"ACCT_NOT_FOUND\"}")));

        mvc.perform(get("/api/accounts/99999999").header("Authorization", gdv))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ACCT_NOT_FOUND"));
    }

    private static MappingBuilder postingEndpoint() {
        return WireMock.post("/core/postings");
    }

    private static String posting(String ref) {
        return """
                {"postingId":"PST00000077","referenceNo":"%s","accountNo":"23122002","direction":"DEBIT",
                 "amount":500000,"currency":"VND","balanceAfter":1000000,"postedAt":"2026-10-25T03:00:00Z"}
                """.formatted(ref);
    }
}
