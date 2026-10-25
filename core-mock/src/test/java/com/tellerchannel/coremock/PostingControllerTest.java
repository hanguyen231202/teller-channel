package com.tellerchannel.coremock;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PostingController.class)
class PostingControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    PostingService postingService;

    @MockitoBean
    ChaosService chaosService;

    private static final String BODY = """
            {"referenceNo":"TC001","accountNo":"23122002","direction":"DEBIT",
             "amount":500000,"currency":"VND","description":"Rut tien"}
            """;

    private static Posting posting() {
        return new Posting("PST00000001", "TC001", "23122002", PostingDirection.DEBIT,
                new BigDecimal("500000"), "VND", new BigDecimal("1000000"), "Rut tien", Instant.now());
    }

    @Test
    void newPosting_returns201() throws Exception {
        when(postingService.post(any())).thenReturn(new PostingService.PostingResult(posting(), false));

        mockMvc.perform(post("/core/postings").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.postingId").value("PST00000001"));
    }

    @Test
    void replayedPosting_returns200() throws Exception {
        when(postingService.post(any())).thenReturn(new PostingService.PostingResult(posting(), true));

        mockMvc.perform(post("/core/postings").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk());
    }

    @Test
    void insufficientFunds_returns422WithCode() throws Exception {
        when(postingService.post(any())).thenThrow(
                new CoreBusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "INSUFFICIENT_FUNDS", "no money"));

        mockMvc.perform(post("/core/postings").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_FUNDS"));
    }

    @Test
    void negativeAmount_returns400() throws Exception {
        String body = BODY.replace("500000", "-1");

        mockMvc.perform(post("/core/postings").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        verifyNoInteractions(postingService);
    }

    @Test
    void inquiry_unknownReference_returns404PostingNotFound() throws Exception {
        when(postingService.findByReference("TC999")).thenReturn(Optional.empty());

        mockMvc.perform(get("/core/postings/TC999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("POSTING_NOT_FOUND"));
    }
}
