package com.tellerchannel.channel.transaction;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Giao dịch tại quầy, nhìn từ phía KÊNH: ai lập, ai duyệt, đã gửi core chưa, core trả gì.
 * Không có số dư ở đây.
 */
@Entity
@Table(name = "teller_txn")
public class TellerTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "txn_ref", nullable = false, unique = true, length = 30)
    private String transactionRef;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 64)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "txn_type", nullable = false, length = 20)
    private TransactionType type;

    @Column(name = "account_no", nullable = false, length = 20)
    private String accountNo;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransactionStatus status;

    @Column(name = "maker_id", nullable = false, length = 50)
    private String makerId;

    @Column(name = "checker_id", length = 50)
    private String checkerId;

    @Column(name = "reject_reason")
    private String rejectReason;

    @Column(name = "core_posting_id", length = 50)
    private String corePostingId;

    @Column(name = "core_error_code", length = 50)
    private String coreErrorCode;

    @Column(name = "core_error_message", length = 500)
    private String coreErrorMessage;

    @Column(name = "reconcile_attempts", nullable = false)
    private int reconcileAttempts;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "sent_to_core_at")
    private Instant sentToCoreAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "last_reconcile_at")
    private Instant lastReconcileAt;

    /** Optimistic lock: hai KSV cùng duyệt một lúc thì người thứ hai bị lỗi thay vì ghi đè. */
    @Version
    private long version;

    /** JPA cần constructor không tham số. */
    protected TellerTransaction() {
    }

    /** Cách duy nhất để tạo giao dịch mới: luôn bắt đầu ở INITIATED. */
    public static TellerTransaction initiate(String transactionRef, String idempotencyKey, TransactionType type,
                                             String accountNo, BigDecimal amount, String currency,
                                             String description, String makerId, Instant now) {
        TellerTransaction t = new TellerTransaction();
        t.transactionRef = transactionRef;
        t.idempotencyKey = idempotencyKey;
        t.type = type;
        t.accountNo = accountNo;
        t.amount = amount;
        t.currency = currency;
        t.description = description;
        t.makerId = makerId;
        t.status = TransactionStatus.INITIATED;
        t.createdAt = now;
        t.updatedAt = now;
        return t;
    }

    public Long getId() { return id; }
    public String getTransactionRef() { return transactionRef; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public TransactionType getType() { return type; }
    public String getAccountNo() { return accountNo; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public String getDescription() { return description; }
    public TransactionStatus getStatus() { return status; }
    public String getMakerId() { return makerId; }
    public String getCheckerId() { return checkerId; }
    public String getRejectReason() { return rejectReason; }
    public String getCorePostingId() { return corePostingId; }
    public String getCoreErrorCode() { return coreErrorCode; }
    public String getCoreErrorMessage() { return coreErrorMessage; }
    public int getReconcileAttempts() { return reconcileAttempts; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getSubmittedAt() { return submittedAt; }
    public Instant getApprovedAt() { return approvedAt; }
    public Instant getSentToCoreAt() { return sentToCoreAt; }
    public Instant getCompletedAt() { return completedAt; }
    public Instant getLastReconcileAt() { return lastReconcileAt; }
    public long getVersion() { return version; }
}
