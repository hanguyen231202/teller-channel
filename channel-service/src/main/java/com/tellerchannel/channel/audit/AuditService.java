package com.tellerchannel.channel.audit;

import com.tellerchannel.channel.transaction.TellerTransaction;
import com.tellerchannel.channel.transaction.TransactionStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;

@Service
public class AuditService {

    private final AuditLogRepository repository;
    private final Clock clock;

    public AuditService(AuditLogRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /**
     * MANDATORY: bắt buộc phải đang ở trong một transaction có sẵn, nếu không thì ném lỗi.
     * Nhờ vậy audit luôn commit/rollback CÙNG với việc đổi trạng thái: không có chuyện
     * trạng thái đổi mà không có log, hoặc có log mà trạng thái không đổi.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(TellerTransaction txn, String action, TransactionStatus fromStatus, String actor, String detail) {
        repository.save(new AuditLog(txn.getId(), action, fromStatus, txn.getStatus(), actor,
                detail == null || detail.length() <= 500 ? detail : detail.substring(0, 500), clock.instant()));
    }

    @Transactional(readOnly = true)
    public List<AuditLog> trail(Long transactionId) {
        return repository.findByTransactionIdOrderByIdAsc(transactionId);
    }
}
