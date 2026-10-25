package com.tellerchannel.coremock;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class PostingService {

    private final AccountService accountService;
    private final Clock clock;
    private final Map<String, Posting> postingsByRef = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong();

    public PostingService(AccountService accountService, Clock clock) {
        this.accountService = accountService;
        this.clock = clock;
    }

    /**
     * Hạch toán một lệnh. Idempotent theo referenceNo:
     * gửi lại cùng mã tham chiếu thì trả bút toán cũ, không trừ/cộng tiền lần nữa.
     *
     * synchronized: core thật dùng khóa dòng trong DB (SELECT ... FOR UPDATE).
     * Ở mock một JVM, khóa cả method là cách đơn giản và đúng để
     * "kiểm tra trùng → kiểm tra số dư → ghi" diễn ra nguyên khối.
     */
    public synchronized PostingResult post(PostingRequest req) {
        Posting existing = postingsByRef.get(req.referenceNo());
        if (existing != null) {
            if (!existing.sameContentAs(req)) {
                throw new CoreBusinessException(HttpStatus.CONFLICT, "DUPLICATE_REFERENCE",
                        "Reference " + req.referenceNo() + " already used with different content");
            }
            return new PostingResult(existing, true);
        }

        Account account = accountService.findByAccountNo(req.accountNo());
        if (account.status() == AccountStatus.BLOCKED) {
            throw new CoreBusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "ACCT_BLOCKED",
                    "Account " + req.accountNo() + " is blocked");
        }
        if (!account.currency().equals(req.currency())) {
            throw new CoreBusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "CURRENCY_MISMATCH",
                    "Account currency is " + account.currency());
        }

        BigDecimal newBalance = switch (req.direction()) {
            case CREDIT -> account.balance().add(req.amount());
            case DEBIT -> account.balance().subtract(req.amount());
        };
        if (newBalance.signum() < 0) {
            throw new CoreBusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "INSUFFICIENT_FUNDS",
                    "Insufficient funds in account " + req.accountNo());
        }

        accountService.updateBalance(account.accountNo(), newBalance);
        Instant now = clock.instant();
        Posting posting = new Posting(
                "PST%08d".formatted(sequence.incrementAndGet()),
                req.referenceNo(), req.accountNo(), req.direction(), req.amount(), req.currency(),
                newBalance, req.description(), now);
        postingsByRef.put(posting.referenceNo(), posting);
        return new PostingResult(posting, false);
    }

    /** Kênh gọi khi đối soát: "giao dịch có mã tham chiếu này đã hạch toán chưa?" */
    public Optional<Posting> findByReference(String referenceNo) {
        return Optional.ofNullable(postingsByRef.get(referenceNo));
    }

    /** replayed = true nghĩa là lệnh trùng, trả lại bút toán đã có. */
    public record PostingResult(Posting posting, boolean replayed) {
    }
}
