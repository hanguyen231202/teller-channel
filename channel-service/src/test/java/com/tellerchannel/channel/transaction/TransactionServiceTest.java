package com.tellerchannel.channel.transaction;

import com.tellerchannel.channel.audit.AuditLog;
import com.tellerchannel.channel.common.ApiException;
import com.tellerchannel.channel.core.CoreBankingAdapter;
import com.tellerchannel.channel.core.CorePosting;
import com.tellerchannel.channel.core.CorePostingRequest;
import com.tellerchannel.channel.core.PostingOutcome;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Test tầng service với DB thật (H2) và core giả (Mockito).
 * Mục tiêu: kiểm tra luồng trạng thái + audit + idempotency, không quan tâm HTTP.
 */
@SpringBootTest(properties = "teller.reconciliation.enabled=false")
class TransactionServiceTest {

    @Autowired
    TransactionService service;

    @MockitoBean
    CoreBankingAdapter core;

    private static CreateTransactionRequest withdraw(String amount) {
        return new CreateTransactionRequest(TransactionType.WITHDRAWAL, "23122002", new BigDecimal(amount), "VND", "Rut tien");
    }

    private TellerTransaction pendingTxn() {
        TellerTransaction t = service.create(withdraw("500000"), "gdv01", UUID.randomUUID().toString()).transaction();
        return service.submit(t.getId(), "gdv01");
    }

    private static CorePosting posting(String ref) {
        return new CorePosting("PST1", ref, "23122002", "DEBIT", new BigDecimal("500000"), "VND",
                new BigDecimal("1000000"), Instant.now());
    }

    @Test
    void approve_corePosted_isSuccess_withFullAuditTrail() {
        TellerTransaction t = pendingTxn();
        when(core.post(any())).thenAnswer(inv -> new PostingOutcome.Posted(posting(t.getTransactionRef())));

        TellerTransaction result = service.approve(t.getId(), "ksv01");

        assertEquals(TransactionStatus.SUCCESS, result.getStatus());
        assertEquals("PST1", result.getCorePostingId());
        List<String> actions = service.auditTrail(t.getId()).stream().map(AuditLog::getAction).toList();
        assertEquals(List.of("CREATE", "SUBMIT", "APPROVE", "CORE_RESULT"), actions);
    }

    @Test
    void approve_sendsWithdrawalAsDebitWithTransactionRef() {
        TellerTransaction t = pendingTxn();
        when(core.post(any())).thenAnswer(inv -> new PostingOutcome.Posted(posting(t.getTransactionRef())));

        service.approve(t.getId(), "ksv01");

        ArgumentCaptor<CorePostingRequest> captor = ArgumentCaptor.forClass(CorePostingRequest.class);
        verify(core).post(captor.capture());
        assertEquals(CorePostingRequest.Direction.DEBIT, captor.getValue().direction());
        assertEquals(t.getTransactionRef(), captor.getValue().referenceNo());
    }

    @Test
    void approve_coreRejected_isFailedWithCoreCode() {
        TellerTransaction t = pendingTxn();
        when(core.post(any())).thenReturn(new PostingOutcome.Rejected("INSUFFICIENT_FUNDS", "no money"));

        TellerTransaction result = service.approve(t.getId(), "ksv01");

        assertEquals(TransactionStatus.FAILED, result.getStatus());
        assertEquals("INSUFFICIENT_FUNDS", result.getCoreErrorCode());
    }

    @Test
    void approve_coreTimeout_isUnknown() {
        TellerTransaction t = pendingTxn();
        when(core.post(any())).thenReturn(new PostingOutcome.Unknown("TIMEOUT: request timed out"));

        TellerTransaction result = service.approve(t.getId(), "ksv01");

        assertEquals(TransactionStatus.UNKNOWN, result.getStatus());
    }

    @Test
    void approve_coreUnreachable_isFailedCoreUnreachable() {
        TellerTransaction t = pendingTxn();
        when(core.post(any())).thenReturn(new PostingOutcome.NotSent("ConnectException"));

        TellerTransaction result = service.approve(t.getId(), "ksv01");

        assertEquals(TransactionStatus.FAILED, result.getStatus());
        assertEquals("CORE_UNREACHABLE", result.getCoreErrorCode());
    }

    @Test
    void selfApproval_isRejected_andCoreNeverCalled() {
        TellerTransaction t = pendingTxn();

        ApiException ex = assertThrows(ApiException.class, () -> service.approve(t.getId(), "gdv01"));

        assertEquals("SELF_APPROVAL_NOT_ALLOWED", ex.getCode());
        verifyNoInteractions(core);
        assertEquals(TransactionStatus.PENDING_APPROVAL, service.get(t.getId()).getStatus());
    }

    @Test
    void sameIdempotencyKey_returnsSameTransaction() {
        String key = UUID.randomUUID().toString();

        TransactionService.CreateResult first = service.create(withdraw("500000"), "gdv01", key);
        TransactionService.CreateResult second = service.create(withdraw("500000.00"), "gdv01", key);

        assertFalse(first.replayed());
        assertTrue(second.replayed());
        assertEquals(first.transaction().getId(), second.transaction().getId());
    }

    @Test
    void sameIdempotencyKey_differentAmount_isConflict() {
        String key = UUID.randomUUID().toString();
        service.create(withdraw("500000"), "gdv01", key);

        ApiException ex = assertThrows(ApiException.class, () -> service.create(withdraw("700000"), "gdv01", key));
        assertEquals("IDEMPOTENCY_KEY_REUSED", ex.getCode());
    }

    @Test
    void reject_requiresDifferentUser_andIsFinal() {
        TellerTransaction t = pendingTxn();

        TellerTransaction rejected = service.reject(t.getId(), "ksv01", "Sai so tien");

        assertEquals(TransactionStatus.REJECTED, rejected.getStatus());
        assertThrows(ApiException.class, () -> service.approve(t.getId(), "ksv02"));
    }
}
