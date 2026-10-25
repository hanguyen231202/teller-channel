package com.tellerchannel.channel.reconciliation;

import com.tellerchannel.channel.audit.AuditLog;
import com.tellerchannel.channel.core.CoreBankingAdapter;
import com.tellerchannel.channel.core.CorePosting;
import com.tellerchannel.channel.core.CoreProperties;
import com.tellerchannel.channel.core.InquiryOutcome;
import com.tellerchannel.channel.core.PostingOutcome;
import com.tellerchannel.channel.support.MutableClock;
import com.tellerchannel.channel.transaction.CreateTransactionRequest;
import com.tellerchannel.channel.transaction.TellerTransaction;
import com.tellerchannel.channel.transaction.TransactionService;
import com.tellerchannel.channel.transaction.TransactionStatus;
import com.tellerchannel.channel.transaction.TransactionType;
import com.tellerchannel.channel.transaction.TransactionWriter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Dùng MutableClock để "tua" qua min-age thay vì chờ thật 30s. */
@SpringBootTest(properties = "teller.reconciliation.enabled=false")
class ReconciliationServiceTest {

    @TestConfiguration
    static class ClockConfig {
        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock(Instant.parse("2026-10-25T03:00:00Z"));
        }
    }

    @Autowired
    TransactionService transactionService;

    @Autowired
    TransactionWriter writer;

    @Autowired
    ReconciliationService reconciliation;

    @Autowired
    MutableClock clock;

    @MockitoBean
    CoreBankingAdapter core;

    private TellerTransaction unknownTxn(String amount) {
        TellerTransaction t = transactionService.create(new CreateTransactionRequest(TransactionType.WITHDRAWAL,
                "23122002", new BigDecimal(amount), "VND", null), "gdv01", UUID.randomUUID().toString()).transaction();
        transactionService.submit(t.getId(), "gdv01");
        when(core.post(any())).thenReturn(new PostingOutcome.Unknown("TIMEOUT"));
        return transactionService.approve(t.getId(), "ksv01");
    }

    private static CorePosting posting(TellerTransaction t, String amount) {
        return new CorePosting("PST42", t.getTransactionRef(), t.getAccountNo(), "DEBIT", new BigDecimal(amount),
                "VND", BigDecimal.ZERO, Instant.now());
    }

    @Test
    void unknown_foundAtCore_becomesSuccess() {
        TellerTransaction t = unknownTxn("500000");
        when(core.findPosting(eq(t.getTransactionRef()))).thenReturn(new InquiryOutcome.Found(posting(t, "500000")));
        clock.advance(Duration.ofMinutes(1));

        reconciliation.reconcileBatch();

        TellerTransaction after = transactionService.get(t.getId());
        assertEquals(TransactionStatus.SUCCESS, after.getStatus());
        assertEquals("PST42", after.getCorePostingId());
        AuditLog last = transactionService.auditTrail(t.getId()).getLast();
        assertEquals("RECONCILE", last.getAction());
        assertEquals(ReconciliationService.ACTOR, last.getActor());
    }

    @Test
    void unknown_notFoundAtCore_becomesFailed() {
        TellerTransaction t = unknownTxn("500000");
        when(core.findPosting(eq(t.getTransactionRef()))).thenReturn(new InquiryOutcome.NotFound());
        clock.advance(Duration.ofMinutes(1));

        reconciliation.reconcileBatch();

        TellerTransaction after = transactionService.get(t.getId());
        assertEquals(TransactionStatus.FAILED, after.getStatus());
        assertEquals("NOT_POSTED_AT_CORE", after.getCoreErrorCode());
    }

    @Test
    void coreStillDown_staysUnknown_andCountsAttempt() {
        TellerTransaction t = unknownTxn("500000");
        when(core.findPosting(eq(t.getTransactionRef()))).thenReturn(new InquiryOutcome.Unavailable("503"));
        clock.advance(Duration.ofMinutes(1));

        reconciliation.reconcileBatch();

        TellerTransaction after = transactionService.get(t.getId());
        assertEquals(TransactionStatus.UNKNOWN, after.getStatus());
        assertEquals(1, after.getReconcileAttempts());
    }

    @Test
    void tooRecent_isNotReconciledYet() {
        TellerTransaction t = unknownTxn("500000");
        clock.advance(Duration.ofSeconds(5));

        reconciliation.reconcileBatch();

        verify(core, never()).findPosting(t.getTransactionRef());
    }

    @Test
    void amountMismatch_isNotAutoResolved() {
        TellerTransaction t = unknownTxn("500000");
        when(core.findPosting(eq(t.getTransactionRef()))).thenReturn(new InquiryOutcome.Found(posting(t, "600000")));
        clock.advance(Duration.ofMinutes(1));

        reconciliation.reconcileBatch();

        TellerTransaction after = transactionService.get(t.getId());
        assertEquals(TransactionStatus.UNKNOWN, after.getStatus());
        assertEquals("RECONCILE_MISMATCH", transactionService.auditTrail(t.getId()).getLast().getAction());
    }

    /** App sập ngay sau khi commit SENT_TO_CORE (chưa kịp ghi kết quả): đối soát vẫn tìm thấy và chốt. */
    @Test
    void stuckSentToCore_afterCrash_isReconciled() {
        TellerTransaction t = transactionService.create(new CreateTransactionRequest(TransactionType.DEPOSIT,
                "23122002", new BigDecimal("100000"), "VND", null), "gdv01", UUID.randomUUID().toString()).transaction();
        transactionService.submit(t.getId(), "gdv01");
        writer.approveAndMarkSent(t.getId(), "ksv01");   // "sập" ở đây: không gọi core, không ghi kết quả
        when(core.findPosting(eq(t.getTransactionRef()))).thenReturn(new InquiryOutcome.NotFound());
        clock.advance(Duration.ofMinutes(1));

        reconciliation.reconcileBatch();

        assertEquals(TransactionStatus.FAILED, transactionService.get(t.getId()).getStatus());
    }

    @Test
    void unsafeMinAge_failsFast() {
        CoreProperties coreProps = new CoreProperties("http://x", Duration.ofSeconds(2), Duration.ofSeconds(5),
                2, 3, Duration.ofMillis(200));
        ReconciliationProperties tooShort = new ReconciliationProperties(true, Duration.ofSeconds(10), 50, 10);

        assertThrows(IllegalStateException.class, () -> ReconciliationService.requireSafeMinAge(tooShort, coreProps));
    }
}
