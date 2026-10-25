package com.tellerchannel.channel.audit;

import com.tellerchannel.channel.transaction.TransactionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** Một dòng nhật ký: ai, làm gì, lúc nào, trạng thái từ gì sang gì. Không có setter: ghi một lần. */
@Entity
@Table(name = "txn_audit_log")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "txn_id", nullable = false)
    private Long transactionId;

    @Column(nullable = false, length = 30)
    private String action;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 20)
    private TransactionStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 20)
    private TransactionStatus toStatus;

    @Column(nullable = false, length = 50)
    private String actor;

    @Column(length = 500)
    private String detail;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AuditLog() {
    }

    public AuditLog(Long transactionId, String action, TransactionStatus fromStatus, TransactionStatus toStatus,
                    String actor, String detail, Instant createdAt) {
        this.transactionId = transactionId;
        this.action = action;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.actor = actor;
        this.detail = detail;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public Long getTransactionId() { return transactionId; }
    public String getAction() { return action; }
    public TransactionStatus getFromStatus() { return fromStatus; }
    public TransactionStatus getToStatus() { return toStatus; }
    public String getActor() { return actor; }
    public String getDetail() { return detail; }
    public Instant getCreatedAt() { return createdAt; }
}
