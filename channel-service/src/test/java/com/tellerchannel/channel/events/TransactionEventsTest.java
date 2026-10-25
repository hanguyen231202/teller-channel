package com.tellerchannel.channel.events;

import com.tellerchannel.channel.core.CoreBankingAdapter;
import com.tellerchannel.channel.core.PostingOutcome;
import com.tellerchannel.channel.transaction.CreateTransactionRequest;
import com.tellerchannel.channel.transaction.TellerTransaction;
import com.tellerchannel.channel.transaction.TransactionService;
import com.tellerchannel.channel.transaction.TransactionStatus;
import com.tellerchannel.channel.transaction.TransactionType;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = "teller.reconciliation.enabled=false")
class TransactionEventsTest {

    @Autowired
    TransactionService service;

    @MockitoBean
    CoreBankingAdapter core;

    @MockitoBean
    TransactionEventSink sink;

    private TellerTransaction pending() {
        TellerTransaction t = service.create(new CreateTransactionRequest(TransactionType.DEPOSIT, "23122002",
                new BigDecimal("100000"), "VND", null), "gdv01", UUID.randomUUID().toString()).transaction();
        return service.submit(t.getId(), "gdv01");
    }

    @Test
    void finalStatus_publishesOneEventAfterCommit() {
        TellerTransaction t = pending();
        when(core.post(any())).thenReturn(new PostingOutcome.Rejected("ACCT_BLOCKED", "blocked"));

        service.approve(t.getId(), "ksv01");

        ArgumentCaptor<TransactionCompletedEvent> captor = ArgumentCaptor.forClass(TransactionCompletedEvent.class);
        verify(sink, times(1)).publish(captor.capture());
        assertEquals(TransactionStatus.FAILED, captor.getValue().status());
        assertEquals(t.getTransactionRef(), captor.getValue().transactionRef());
    }

    @Test
    void unknownStatus_isNotFinal_soNoEvent() {
        TellerTransaction t = pending();
        when(core.post(any())).thenReturn(new PostingOutcome.Unknown("TIMEOUT"));

        service.approve(t.getId(), "ksv01");

        verifyNoInteractions(sink);
    }

    @Test
    void rejectedByChecker_publishesEvent() {
        TellerTransaction t = pending();

        service.reject(t.getId(), "ksv01", "Sai thong tin");

        verify(sink).publish(argThat(e -> e.status() == TransactionStatus.REJECTED));
    }
}
