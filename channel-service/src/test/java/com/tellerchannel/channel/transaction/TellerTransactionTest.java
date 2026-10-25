package com.tellerchannel.channel.transaction;

import com.tellerchannel.channel.common.ApiException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/** Unit test thuần cho quy tắc nghiệp vụ trong entity: không Spring, không DB. */
class TellerTransactionTest {

    private static final Instant NOW = Instant.parse("2026-10-25T03:00:00Z");

    private static TellerTransaction newTxn() {
        return TellerTransaction.initiate("TC1", "key-1", TransactionType.WITHDRAWAL, "23122002",
                new BigDecimal("500000"), "VND", null, "gdv01", NOW);
    }

    private static TellerTransaction pending() {
        TellerTransaction t = newTxn();
        t.submit("gdv01", NOW);
        return t;
    }

    @Test
    void happyPath_initiated_pending_sent_success() {
        TellerTransaction t = pending();
        assertEquals(TransactionStatus.PENDING_APPROVAL, t.getStatus());

        t.approve("ksv01", NOW);
        assertEquals(TransactionStatus.SENT_TO_CORE, t.getStatus());
        assertEquals("ksv01", t.getCheckerId());

        t.markSuccess("PST1", NOW);
        assertEquals(TransactionStatus.SUCCESS, t.getStatus());
        assertTrue(t.getStatus().isFinal());
    }

    @Test
    void makerCannotApproveOwnTransaction() {
        TellerTransaction t = pending();

        ApiException ex = assertThrows(ApiException.class, () -> t.approve("gdv01", NOW));
        assertEquals("SELF_APPROVAL_NOT_ALLOWED", ex.getCode());
        assertEquals(TransactionStatus.PENDING_APPROVAL, t.getStatus());
    }

    @Test
    void onlyMakerCanSubmit() {
        ApiException ex = assertThrows(ApiException.class, () -> newTxn().submit("gdv02", NOW));
        assertEquals("NOT_TRANSACTION_MAKER", ex.getCode());
    }

    @Test
    void cannotApproveBeforeSubmit() {
        ApiException ex = assertThrows(ApiException.class, () -> newTxn().approve("ksv01", NOW));
        assertEquals("INVALID_STATE", ex.getCode());
    }

    @Test
    void cannotApproveTwice() {
        TellerTransaction t = pending();
        t.approve("ksv01", NOW);

        assertThrows(ApiException.class, () -> t.approve("ksv02", NOW));
    }

    @Test
    void unknown_canBeResolvedBySuccessOrFailure_butFinalStatesAreFinal() {
        TellerTransaction t = pending();
        t.approve("ksv01", NOW);
        t.markUnknown("TIMEOUT", NOW);
        assertEquals(TransactionStatus.UNKNOWN, t.getStatus());

        t.markFailed("NOT_POSTED_AT_CORE", "x", NOW);
        assertEquals(TransactionStatus.FAILED, t.getStatus());
        assertThrows(ApiException.class, () -> t.markSuccess("PST1", NOW));
    }

    @Test
    void transitionTable() {
        assertTrue(TransactionStatus.INITIATED.canTransitionTo(TransactionStatus.PENDING_APPROVAL));
        assertFalse(TransactionStatus.INITIATED.canTransitionTo(TransactionStatus.SENT_TO_CORE));
        assertFalse(TransactionStatus.PENDING_APPROVAL.canTransitionTo(TransactionStatus.UNKNOWN));
        assertFalse(TransactionStatus.SUCCESS.canTransitionTo(TransactionStatus.FAILED));
        assertFalse(TransactionStatus.REJECTED.canTransitionTo(TransactionStatus.PENDING_APPROVAL));
    }
}
