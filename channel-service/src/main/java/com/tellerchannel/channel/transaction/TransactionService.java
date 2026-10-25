package com.tellerchannel.channel.transaction;

import com.tellerchannel.channel.audit.AuditLog;
import com.tellerchannel.channel.audit.AuditService;
import com.tellerchannel.channel.common.ApiException;
import com.tellerchannel.channel.core.CoreBankingAdapter;
import com.tellerchannel.channel.core.CorePostingRequest;
import com.tellerchannel.channel.core.PostingOutcome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Điều phối vòng đời giao dịch. Class này KHÔNG có @Transactional:
 * mỗi bước ghi DB đi qua TransactionWriter (transaction riêng, ngắn), còn gọi core nằm ở giữa, ngoài transaction.
 */
@Service
public class TransactionService {

    private static final Logger log = LoggerFactory.getLogger(TransactionService.class);

    private final TellerTransactionRepository repository;
    private final TransactionWriter writer;
    private final CoreBankingAdapter coreBankingAdapter;
    private final AuditService auditService;
    private final Clock clock;

    public TransactionService(TellerTransactionRepository repository, TransactionWriter writer,
                              CoreBankingAdapter coreBankingAdapter, AuditService auditService, Clock clock) {
        this.repository = repository;
        this.writer = writer;
        this.coreBankingAdapter = coreBankingAdapter;
        this.auditService = auditService;
        this.clock = clock;
    }

    /**
     * Idempotent theo Idempotency-Key:
     *  - key mới → tạo giao dịch.
     *  - key đã có, cùng nội dung → trả giao dịch cũ (GDV bấm "Gửi" 2 lần vì mạng lag).
     *  - key đã có, khác nội dung → 409, vì nhiều khả năng client dùng nhầm key.
     * Hai request cùng key đến ĐỒNG THỜI: cả hai cùng không thấy key, cùng INSERT; ràng buộc UNIQUE
     * trong DB chặn request thứ hai (DataIntegrityViolationException), ta đọc lại bản đã có.
     */
    public CreateResult create(CreateTransactionRequest req, String maker, String idempotencyKey) {
        var existing = repository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            return new CreateResult(requireSamePayload(existing.get(), req, maker), true);
        }
        TellerTransaction txn = TellerTransaction.initiate(newTransactionRef(), idempotencyKey, req.type(),
                req.accountNo(), req.amount(), req.currency(), req.description(), maker, clock.instant());
        try {
            return new CreateResult(writer.create(txn), false);
        } catch (DataIntegrityViolationException race) {
            TellerTransaction winner = repository.findByIdempotencyKey(idempotencyKey).orElseThrow(() -> race);
            return new CreateResult(requireSamePayload(winner, req, maker), true);
        }
    }

    public TellerTransaction submit(Long id, String actor) {
        return writer.submit(id, actor);
    }

    /**
     * Luồng duyệt, 3 bước tách biệt:
     *  1. [transaction 1] PENDING_APPROVAL → SENT_TO_CORE, COMMIT.
     *  2. Gọi core (ngoài transaction). App sập ở đây thì DB vẫn ghi SENT_TO_CORE → đối soát sẽ xử lý.
     *  3. [transaction 2] Ghi kết quả: SUCCESS / FAILED / UNKNOWN.
     */
    public TellerTransaction approve(Long id, String checker) {
        CorePostingRequest postingRequest = writer.approveAndMarkSent(id, checker);
        log.info("Transaction {} approved by {}, sending to core", postingRequest.referenceNo(), checker);
        PostingOutcome outcome = coreBankingAdapter.post(postingRequest);
        TellerTransaction result = writer.applyPostingOutcome(id, outcome, checker);
        log.info("Transaction {} finished approval flow with status {}", result.getTransactionRef(), result.getStatus());
        return result;
    }

    public TellerTransaction reject(Long id, String checker, String reason) {
        return writer.reject(id, checker, reason);
    }

    public TellerTransaction get(Long id) {
        return writer.load(id);
    }

    public Page<TellerTransaction> list(TransactionStatus status, Pageable pageable) {
        return status == null ? repository.findAll(pageable) : repository.findByStatus(status, pageable);
    }

    public List<AuditLog> auditTrail(Long id) {
        writer.load(id);
        return auditService.trail(id);
    }

    private static TellerTransaction requireSamePayload(TellerTransaction t, CreateTransactionRequest req, String maker) {
        boolean same = t.getType() == req.type()
                && t.getAccountNo().equals(req.accountNo())
                && t.getAmount().compareTo(req.amount()) == 0
                && t.getCurrency().equals(req.currency())
                && t.getMakerId().equals(maker);
        if (!same) {
            throw new ApiException(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_REUSED",
                    "Idempotency-Key was already used for a different transaction");
        }
        return t;
    }

    /** Mã tham chiếu gửi core: "TC" + 20 ký tự hex, duy nhất, không đoán được. */
    private static String newTransactionRef() {
        return "TC" + UUID.randomUUID().toString().replace("-", "").substring(0, 20).toUpperCase(Locale.ROOT);
    }

    public record CreateResult(TellerTransaction transaction, boolean replayed) {
    }
}
