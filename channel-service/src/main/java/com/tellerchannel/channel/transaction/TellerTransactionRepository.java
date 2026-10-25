package com.tellerchannel.channel.transaction;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TellerTransactionRepository extends JpaRepository<TellerTransaction, Long> {

    Optional<TellerTransaction> findByIdempotencyKey(String idempotencyKey);

    Optional<TellerTransaction> findByTransactionRef(String transactionRef);

    Page<TellerTransaction> findByStatus(TransactionStatus status, Pageable pageable);
}
