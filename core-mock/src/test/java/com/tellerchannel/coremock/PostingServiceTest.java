package com.tellerchannel.coremock;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class PostingServiceTest {

    private final AccountService accountService = new AccountService();
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-25T03:00:00Z"), ZoneOffset.UTC);
    private final PostingService service = new PostingService(accountService, clock);

    private static PostingRequest debit(String ref, String accountNo, String amount) {
        return new PostingRequest(ref, accountNo, PostingDirection.DEBIT, new BigDecimal(amount), "VND", "Rut tien");
    }

    @Test
    void debit_enoughBalance_decreasesBalance() {
        PostingService.PostingResult result = service.post(debit("REF1", "23122002", "500000"));

        assertFalse(result.replayed());
        assertEquals(0, new BigDecimal("1000000").compareTo(result.posting().balanceAfter()));
        assertEquals(0, new BigDecimal("1000000").compareTo(accountService.findByAccountNo("23122002").balance()));
    }

    @Test
    void credit_increasesBalance() {
        service.post(new PostingRequest("REF1", "23122002", PostingDirection.CREDIT, new BigDecimal("250000.50"), "VND", null));

        assertEquals(0, new BigDecimal("1750000.50").compareTo(accountService.findByAccountNo("23122002").balance()));
    }

    @Test
    void sameReferenceTwice_postsOnlyOnce() {
        PostingService.PostingResult first = service.post(debit("REF1", "23122002", "500000"));
        PostingService.PostingResult second = service.post(debit("REF1", "23122002", "500000"));

        assertTrue(second.replayed());
        assertEquals(first.posting().postingId(), second.posting().postingId());
        assertEquals(0, new BigDecimal("1000000").compareTo(accountService.findByAccountNo("23122002").balance()));
    }

    @Test
    void sameReferenceDifferentAmount_isRejected() {
        service.post(debit("REF1", "23122002", "500000"));

        CoreBusinessException ex = assertThrows(CoreBusinessException.class,
                () -> service.post(debit("REF1", "23122002", "600000")));
        assertEquals("DUPLICATE_REFERENCE", ex.getCode());
    }

    @Test
    void debit_moreThanBalance_isRejectedAndBalanceUnchanged() {
        CoreBusinessException ex = assertThrows(CoreBusinessException.class,
                () -> service.post(debit("REF1", "23122002", "1500000.01")));

        assertEquals("INSUFFICIENT_FUNDS", ex.getCode());
        assertEquals(0, new BigDecimal("1500000").compareTo(accountService.findByAccountNo("23122002").balance()));
        assertTrue(service.findByReference("REF1").isEmpty());
    }

    @Test
    void blockedAccount_isRejected() {
        CoreBusinessException ex = assertThrows(CoreBusinessException.class,
                () -> service.post(debit("REF1", "88889999", "1000")));
        assertEquals("ACCT_BLOCKED", ex.getCode());
    }

    @Test
    void wrongCurrency_isRejected() {
        CoreBusinessException ex = assertThrows(CoreBusinessException.class,
                () -> service.post(new PostingRequest("REF1", "23122002", PostingDirection.CREDIT, BigDecimal.TEN, "USD", null)));
        assertEquals("CURRENCY_MISMATCH", ex.getCode());
    }

    /** 20 thread cùng rút 100.000 từ tài khoản 1.500.000: đúng 15 lệnh thành công, số dư về 0, không âm. */
    @Test
    void concurrentDebits_neverOverdraw() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(20);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger ok = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        for (int i = 0; i < 20; i++) {
            String ref = "REF" + i;
            pool.submit(() -> {
                start.await();
                try {
                    service.post(debit(ref, "23122002", "100000"));
                    ok.incrementAndGet();
                } catch (CoreBusinessException e) {
                    rejected.incrementAndGet();
                }
                return null;
            });
        }
        start.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));

        assertEquals(15, ok.get());
        assertEquals(5, rejected.get());
        assertEquals(0, BigDecimal.ZERO.compareTo(accountService.findByAccountNo("23122002").balance()));
    }
}
