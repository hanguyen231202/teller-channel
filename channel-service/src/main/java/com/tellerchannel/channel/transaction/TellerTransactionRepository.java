package com.tellerchannel.channel.transaction;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TellerTransactionRepository extends JpaRepository<TellerTransaction, Long> {

    Optional<TellerTransaction> findByIdempotencyKey(String idempotencyKey);

    Optional<TellerTransaction> findByTransactionRef(String transactionRef);

    Page<TellerTransaction> findByStatus(TransactionStatus status, Pageable pageable);

    /** Giao dịch cần đối soát: đúng trạng thái, đã gửi core đủ lâu, chưa vượt số lần hỏi. Cũ nhất trước. */
    @Query("""
            select t from TellerTransaction t
            where t.status in :statuses
              and t.sentToCoreAt < :sentBefore
              and t.reconcileAttempts < :maxAttempts
            order by t.sentToCoreAt asc
            """)
    List<TellerTransaction> findReconcileCandidates(@Param("statuses") Collection<TransactionStatus> statuses,
                                                    @Param("sentBefore") Instant sentBefore,
                                                    @Param("maxAttempts") int maxAttempts,
                                                    Pageable pageable);
}
