package com.tellerchannel.channel.reconciliation;

import com.tellerchannel.channel.common.ApiException;
import com.tellerchannel.channel.core.CoreBankingAdapter;
import com.tellerchannel.channel.core.CoreProperties;
import com.tellerchannel.channel.core.InquiryOutcome;
import com.tellerchannel.channel.transaction.TellerTransaction;
import com.tellerchannel.channel.transaction.TellerTransactionRepository;
import com.tellerchannel.channel.transaction.TransactionStatus;
import com.tellerchannel.channel.transaction.TransactionWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Đối soát: với giao dịch CHƯA RÕ (hoặc ĐÃ GỬI CORE bị treo do app sập),
 * hỏi core bằng mã tham chiếu rồi chốt theo câu trả lời của core. Core là nguồn sự thật.
 *
 * Không bao giờ "gửi lại lệnh hạch toán" ở đây: chỉ HỎI. Gửi lại có thể trừ tiền hai lần.
 */
@Service
public class ReconciliationService {

    public static final String ACTOR = "SYSTEM-RECON";
    private static final Logger log = LoggerFactory.getLogger(ReconciliationService.class);
    private static final List<TransactionStatus> CANDIDATE_STATUSES =
            List.of(TransactionStatus.UNKNOWN, TransactionStatus.SENT_TO_CORE);

    private final TellerTransactionRepository repository;
    private final TransactionWriter writer;
    private final CoreBankingAdapter coreBankingAdapter;
    private final ReconciliationProperties props;
    private final Clock clock;

    public ReconciliationService(TellerTransactionRepository repository, TransactionWriter writer,
                                 CoreBankingAdapter coreBankingAdapter, ReconciliationProperties props,
                                 CoreProperties coreProps, Clock clock) {
        this.repository = repository;
        this.writer = writer;
        this.coreBankingAdapter = coreBankingAdapter;
        this.props = props;
        this.clock = clock;
        requireSafeMinAge(props, coreProps);
    }

    /** Một lượt đối soát. Trả về số giao dịch đã xử lý. */
    public int reconcileBatch() {
        Instant sentBefore = clock.instant().minus(props.minAge());
        List<TellerTransaction> candidates = repository.findReconcileCandidates(
                CANDIDATE_STATUSES, sentBefore, props.maxAttempts(), PageRequest.of(0, props.batchSize()));
        if (!candidates.isEmpty()) {
            log.info("Reconciling {} transaction(s)", candidates.size());
        }
        for (TellerTransaction txn : candidates) {
            try {
                reconcile(txn);
            } catch (RuntimeException ex) {
                // Một giao dịch lỗi không được làm dừng cả lô
                log.error("Reconciliation of {} failed: {}", txn.getTransactionRef(), ex.toString());
            }
        }
        return candidates.size();
    }

    /** KSV bấm "đối soát lại" cho một giao dịch, không cần chờ job. */
    public TellerTransaction reconcileNow(Long id) {
        TellerTransaction txn = repository.findById(id).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "TXN_NOT_FOUND", "Transaction " + id + " not found"));
        if (!CANDIDATE_STATUSES.contains(txn.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "INVALID_STATE",
                    "Only UNKNOWN or SENT_TO_CORE transactions can be reconciled");
        }
        return reconcile(txn);
    }

    private TellerTransaction reconcile(TellerTransaction txn) {
        InquiryOutcome outcome = coreBankingAdapter.findPosting(txn.getTransactionRef());
        TellerTransaction result = writer.applyInquiryOutcome(txn.getId(), outcome, ACTOR);
        log.info("Reconciled {}: {} -> {}", txn.getTransactionRef(), txn.getStatus(), result.getStatus());
        return result;
    }

    /**
     * Fail fast khi cấu hình nguy hiểm: nếu min-age nhỏ hơn thời gian luồng duyệt có thể còn đang chờ core,
     * job có thể hỏi core lúc lệnh còn đang chạy, nhận "chưa có" và chốt FAILED sai.
     */
    static void requireSafeMinAge(ReconciliationProperties props, CoreProperties core) {
        Duration worstCaseWait = core.connectTimeout().plus(core.readTimeout())
                .plus(core.retryBackoff()).multipliedBy(core.postingMaxAttempts());
        if (props.minAge().compareTo(worstCaseWait) <= 0) {
            throw new IllegalStateException("teller.reconciliation.min-age (" + props.minAge()
                    + ") must be greater than the worst-case core wait (" + worstCaseWait + ")");
        }
    }
}
