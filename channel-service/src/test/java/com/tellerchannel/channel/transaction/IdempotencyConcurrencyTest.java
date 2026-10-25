package com.tellerchannel.channel.transaction;

import com.tellerchannel.channel.core.CoreBankingAdapter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 10 request cùng Idempotency-Key bắn ĐỒNG THỜI: chỉ được tạo đúng 1 giao dịch. */
@SpringBootTest(properties = "teller.reconciliation.enabled=false")
class IdempotencyConcurrencyTest {

    @Autowired
    TransactionService service;

    @MockitoBean
    CoreBankingAdapter core;

    @Test
    void concurrentCreatesWithSameKey_produceOneTransaction() throws Exception {
        String key = UUID.randomUUID().toString();
        CreateTransactionRequest req = new CreateTransactionRequest(TransactionType.DEPOSIT, "23122002",
                new BigDecimal("100000"), "VND", null);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(10);
        try {
            Callable<Long> task = () -> {
                start.await();
                return service.create(req, "gdv01", key).transaction().getId();
            };
            List<Future<Long>> futures = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                futures.add(pool.submit(task));
            }
            start.countDown();
            long distinct = futures.stream().map(f -> {
                try {
                    return f.get();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }).distinct().count();
            assertEquals(1, distinct);
        } finally {
            pool.shutdownNow();
        }
    }
}
