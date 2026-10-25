package com.tellerchannel.channel.core;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import com.tellerchannel.channel.common.ApiException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.ServerSocket;
import java.time.Duration;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test adapter với một "core giả" WireMock chạy HTTP thật, để kiểm tra đúng hành vi timeout/retry.
 * Không cần Spring context: tự lắp adapter như Spring sẽ lắp.
 */
class CoreBankingAdapterTest {

    @RegisterExtension
    static WireMockExtension core = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    private static final String POSTED = """
            {"postingId":"PST00000001","referenceNo":"TC1","accountNo":"23122002","direction":"DEBIT",
             "amount":500000,"currency":"VND","balanceAfter":1000000,"postedAt":"2026-10-25T03:00:00Z"}
            """;

    private static CoreBankingAdapter adapter(String baseUrl) {
        CoreProperties props = new CoreProperties(baseUrl, Duration.ofMillis(500), Duration.ofMillis(500),
                2, 3, Duration.ofMillis(10));
        RestClient client = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(CoreClientConfig.requestFactory(props))
                .build();
        return new CoreBankingAdapter(client, props);
    }

    private static CoreBankingAdapter adapter() {
        return adapter(core.baseUrl());
    }

    private static CorePostingRequest request() {
        return new CorePostingRequest("TC1", "23122002", CorePostingRequest.Direction.DEBIT,
                new BigDecimal("500000"), "VND", "Rut tien");
    }

    private static String problem(int status, String code) {
        return "{\"status\":" + status + ",\"detail\":\"x\",\"code\":\"" + code + "\"}";
    }

    @Test
    void post_201_isPosted() {
        core.stubFor(post("/core/postings").willReturn(
                aResponse().withStatus(201).withHeader("Content-Type", "application/json").withBody(POSTED)));

        PostingOutcome outcome = adapter().post(request());

        PostingOutcome.Posted posted = assertInstanceOf(PostingOutcome.Posted.class, outcome);
        assertEquals("PST00000001", posted.posting().postingId());
        core.verify(postRequestedFor(urlEqualTo("/core/postings"))
                .withRequestBody(matchingJsonPath("$.referenceNo", equalTo("TC1"))));
    }

    @Test
    void post_422_isRejectedWithCoreCode_andNotRetried() {
        core.stubFor(post("/core/postings").willReturn(aResponse().withStatus(422)
                .withHeader("Content-Type", "application/problem+json").withBody(problem(422, "INSUFFICIENT_FUNDS"))));

        PostingOutcome outcome = adapter().post(request());

        assertEquals("INSUFFICIENT_FUNDS", assertInstanceOf(PostingOutcome.Rejected.class, outcome).code());
        core.verify(1, postRequestedFor(urlEqualTo("/core/postings")));
    }

    /** Tình huống trọng tâm: core chậm hơn read timeout → UNKNOWN, và TUYỆT ĐỐI không gửi lại. */
    @Test
    void post_slowerThanReadTimeout_isUnknown_andNotRetried() {
        core.stubFor(post("/core/postings").willReturn(aResponse().withStatus(201).withFixedDelay(1500)
                .withHeader("Content-Type", "application/json").withBody(POSTED)));

        PostingOutcome outcome = adapter().post(request());

        PostingOutcome.Unknown unknown = assertInstanceOf(PostingOutcome.Unknown.class, outcome);
        assertTrue(unknown.reason().startsWith("TIMEOUT"), unknown.reason());
        core.verify(1, postRequestedFor(urlEqualTo("/core/postings")));
    }

    @Test
    void post_503_isUnknown() {
        core.stubFor(post("/core/postings").willReturn(aResponse().withStatus(503)
                .withHeader("Content-Type", "application/problem+json").withBody(problem(503, "CORE_UNAVAILABLE"))));

        assertInstanceOf(PostingOutcome.Unknown.class, adapter().post(request()));
    }

    @Test
    void post_connectionRefused_isNotSentAfterRetries() throws IOException {
        int closedPort;
        try (ServerSocket s = new ServerSocket(0)) {
            closedPort = s.getLocalPort();
        }

        PostingOutcome outcome = adapter("http://localhost:" + closedPort).post(request());

        assertInstanceOf(PostingOutcome.NotSent.class, outcome);
    }

    @Test
    void inquiry_404PostingNotFound_isNotFound() {
        core.stubFor(get("/core/postings/TC1").willReturn(aResponse().withStatus(404)
                .withHeader("Content-Type", "application/problem+json").withBody(problem(404, "POSTING_NOT_FOUND"))));

        assertInstanceOf(InquiryOutcome.NotFound.class, adapter().findPosting("TC1"));
    }

    /** Đọc thì được retry: 503 hai lần rồi 200 → vẫn ra Found. */
    @Test
    void inquiry_retriesTechnicalErrors_thenFound() {
        core.stubFor(get("/core/postings/TC1").inScenario("flaky").whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse().withStatus(503)).willSetStateTo("second"));
        core.stubFor(get("/core/postings/TC1").inScenario("flaky").whenScenarioStateIs("second")
                .willReturn(aResponse().withStatus(503)).willSetStateTo("third"));
        core.stubFor(get("/core/postings/TC1").inScenario("flaky").whenScenarioStateIs("third")
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody(POSTED)));

        InquiryOutcome outcome = adapter().findPosting("TC1");

        assertInstanceOf(InquiryOutcome.Found.class, outcome);
        core.verify(3, getRequestedFor(urlEqualTo("/core/postings/TC1")));
    }

    @Test
    void inquiry_alwaysFailing_isUnavailable() {
        core.stubFor(get("/core/postings/TC1").willReturn(aResponse().withStatus(503)));

        assertInstanceOf(InquiryOutcome.Unavailable.class, adapter().findPosting("TC1"));
        core.verify(3, getRequestedFor(urlEqualTo("/core/postings/TC1")));
    }

    @Test
    void getAccount_404_isApiExceptionWithCoreCode() {
        core.stubFor(get("/core/accounts/99999999").willReturn(aResponse().withStatus(404)
                .withHeader("Content-Type", "application/problem+json").withBody(problem(404, "ACCT_NOT_FOUND"))));

        ApiException ex = assertThrows(ApiException.class, () -> adapter().getAccount("99999999"));
        assertEquals("ACCT_NOT_FOUND", ex.getCode());
        assertEquals(404, ex.getStatus().value());
    }

    @Test
    void getAccount_coreDown_isCoreUnavailable() {
        core.stubFor(get("/core/accounts/23122002").willReturn(aResponse().withStatus(503)));

        ApiException ex = assertThrows(ApiException.class, () -> adapter().getAccount("23122002"));
        assertEquals("CORE_UNAVAILABLE", ex.getCode());
    }
}
