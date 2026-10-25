package com.tellerchannel.channel.transaction;

import com.tellerchannel.channel.audit.AuditService;
import com.tellerchannel.channel.common.ApiException;
import com.tellerchannel.channel.core.CorePosting;
import com.tellerchannel.channel.core.CorePostingRequest;
import com.tellerchannel.channel.core.InquiryOutcome;
import com.tellerchannel.channel.core.PostingOutcome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

/**
 * Mỗi method = MỘT transaction DB ngắn: đọc giao dịch → đổi trạng thái → ghi audit → commit.
 *
 * Vì sao tách ra class riêng mà không để @Transactional trong TransactionService?
 * @Transactional hoạt động qua proxy: gọi method của CHÍNH class mình (this.x()) thì đi vòng qua proxy
 * nên không có transaction. Tách bean riêng thì TransactionService gọi qua proxy, transaction mới có hiệu lực.
 * Đồng thời, việc gọi core (chậm, tới 5s) nằm NGOÀI mọi transaction, không giữ kết nối/khóa DB khi chờ.
 */
@Component
public class TransactionWriter {

    private static final Logger log = LoggerFactory.getLogger(TransactionWriter.class);

    private final TellerTransactionRepository repository;
    private final AuditService auditService;
    private final Clock clock;

    public TransactionWriter(TellerTransactionRepository repository, AuditService auditService, Clock clock) {
        this.repository = repository;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional
    public TellerTransaction create(TellerTransaction txn) {
        TellerTransaction saved = repository.saveAndFlush(txn);
        auditService.record(saved, "CREATE", null, saved.getMakerId(),
                saved.getType() + " " + saved.getAmount().toPlainString() + " " + saved.getCurrency());
        return saved;
    }

    @Transactional
    public TellerTransaction submit(Long id, String actor) {
        TellerTransaction txn = load(id);
        TransactionStatus from = txn.getStatus();
        txn.submit(actor, now());
        auditService.record(txn, "SUBMIT", from, actor, null);
        return txn;
    }

    /** Đổi sang SENT_TO_CORE và trả về lệnh hạch toán. Commit xong mới được gọi core. */
    @Transactional
    public CorePostingRequest approveAndMarkSent(Long id, String checker) {
        TellerTransaction txn = load(id);
        TransactionStatus from = txn.getStatus();
        txn.approve(checker, now());
        auditService.record(txn, "APPROVE", from, checker, "Sending to core with ref " + txn.getTransactionRef());
        CorePostingRequest.Direction direction = switch (txn.getType()) {
            case DEPOSIT -> CorePostingRequest.Direction.CREDIT;
            case WITHDRAWAL -> CorePostingRequest.Direction.DEBIT;
        };
        return new CorePostingRequest(txn.getTransactionRef(), txn.getAccountNo(), direction,
                txn.getAmount(), txn.getCurrency(), txn.getDescription());
    }

    @Transactional
    public TellerTransaction reject(Long id, String checker, String reason) {
        TellerTransaction txn = load(id);
        TransactionStatus from = txn.getStatus();
        txn.reject(checker, reason, now());
        auditService.record(txn, "REJECT", from, checker, reason);
        return txn;
    }

    /** Ghi kết quả gọi core. switch trên sealed interface: thiếu một nhánh là compiler báo lỗi. */
    @Transactional
    public TellerTransaction applyPostingOutcome(Long id, PostingOutcome outcome, String actor) {
        TellerTransaction txn = load(id);
        TransactionStatus from = txn.getStatus();
        if (from != TransactionStatus.SENT_TO_CORE) {
            // Ví dụ: job đối soát đã chốt trước khi luồng duyệt kịp ghi kết quả. Không ghi đè.
            log.warn("Ignoring posting outcome for {}: status is already {}", txn.getTransactionRef(), from);
            return txn;
        }
        Instant now = now();
        String detail = switch (outcome) {
            case PostingOutcome.Posted p -> {
                txn.markSuccess(p.posting().postingId(), now);
                yield "Core posting " + p.posting().postingId();
            }
            case PostingOutcome.Rejected r -> {
                txn.markFailed(r.code(), r.message(), now);
                yield "Core rejected: " + r.code();
            }
            case PostingOutcome.NotSent n -> {
                txn.markFailed("CORE_UNREACHABLE", n.reason(), now);
                yield "Core unreachable, request never sent: " + n.reason();
            }
            case PostingOutcome.Unknown u -> {
                txn.markUnknown(u.reason(), now);
                yield "No definite answer from core: " + u.reason();
            }
        };
        auditService.record(txn, "CORE_RESULT", from, actor, detail);
        return txn;
    }

    /** Ghi kết quả đối soát (hỏi lại core bằng mã tham chiếu). */
    @Transactional
    public TellerTransaction applyInquiryOutcome(Long id, InquiryOutcome outcome, String actor) {
        TellerTransaction txn = load(id);
        TransactionStatus from = txn.getStatus();
        if (from != TransactionStatus.UNKNOWN && from != TransactionStatus.SENT_TO_CORE) {
            return txn;
        }
        Instant now = now();
        switch (outcome) {
            case InquiryOutcome.Found f when matches(txn, f.posting()) -> {
                txn.markSuccess(f.posting().postingId(), now);
                auditService.record(txn, "RECONCILE", from, actor,
                        "Core has posting " + f.posting().postingId() + ": resolved as SUCCESS");
            }
            case InquiryOutcome.Found f -> {
                // Core có bút toán nhưng số tiền/tài khoản khác: không tự chốt, cần người kiểm tra.
                txn.recordReconcileAttempt(now);
                auditService.record(txn, "RECONCILE_MISMATCH", from, actor,
                        "Core posting " + f.posting().postingId() + " does not match transaction");
                log.error("RECONCILIATION MISMATCH for {}: core posting {} {} on {}", txn.getTransactionRef(),
                        f.posting().amount(), f.posting().currency(), f.posting().accountNo());
            }
            case InquiryOutcome.NotFound nf -> {
                txn.markFailed("NOT_POSTED_AT_CORE", "Core has no posting for " + txn.getTransactionRef(), now);
                auditService.record(txn, "RECONCILE", from, actor, "Core has no posting: resolved as FAILED");
            }
            case InquiryOutcome.Unavailable u -> {
                txn.recordReconcileAttempt(now);
                log.warn("Reconciliation of {} postponed (attempt {}): {}", txn.getTransactionRef(),
                        txn.getReconcileAttempts(), u.reason());
            }
        }
        return txn;
    }

    private static boolean matches(TellerTransaction txn, CorePosting posting) {
        return txn.getAccountNo().equals(posting.accountNo())
                && txn.getAmount().compareTo(posting.amount()) == 0
                && txn.getCurrency().equals(posting.currency());
    }

    TellerTransaction load(Long id) {
        return repository.findById(id).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "TXN_NOT_FOUND", "Transaction " + id + " not found"));
    }

    private Instant now() {
        return clock.instant();
    }
}
