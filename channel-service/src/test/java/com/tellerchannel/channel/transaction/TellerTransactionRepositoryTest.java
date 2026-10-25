package com.tellerchannel.channel.transaction;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Slice test tầng JPA: chạy Flyway trên H2 (MODE=Oracle) đã cấu hình,
 * không để Spring thay bằng H2 mặc định (replace = NONE), vì ta muốn kiểm tra đúng script migration.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class TellerTransactionRepositoryTest {

    @Autowired
    TellerTransactionRepository repository;

    private static TellerTransaction txn(String ref, String key) {
        return TellerTransaction.initiate(ref, key, TransactionType.WITHDRAWAL, "23122002",
                new BigDecimal("1000000.50"), "VND", "Rut tien", "gdv01", Instant.parse("2026-10-25T03:00:00Z"));
    }

    @Test
    void saveAndFindByIdempotencyKey() {
        repository.saveAndFlush(txn("TC1", "key-1"));

        TellerTransaction found = repository.findByIdempotencyKey("key-1").orElseThrow();
        assertEquals(TransactionStatus.INITIATED, found.getStatus());
        assertEquals(0, new BigDecimal("1000000.50").compareTo(found.getAmount()));
        assertEquals(0, found.getVersion());
    }

    @Test
    void duplicateIdempotencyKey_isRejectedByDatabase() {
        repository.saveAndFlush(txn("TC1", "key-1"));

        assertThrows(DataIntegrityViolationException.class, () -> repository.saveAndFlush(txn("TC2", "key-1")));
    }
}
