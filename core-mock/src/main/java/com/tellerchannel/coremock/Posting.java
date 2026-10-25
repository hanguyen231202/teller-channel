package com.tellerchannel.coremock;

import java.math.BigDecimal;
import java.time.Instant;

/** Bút toán đã ghi sổ. Đây là "sự thật" mà kênh hỏi lại khi đối soát. */
public record Posting(
        String postingId,
        String referenceNo,
        String accountNo,
        PostingDirection direction,
        BigDecimal amount,
        String currency,
        BigDecimal balanceAfter,
        String description,
        Instant postedAt) {

    /** Cùng referenceNo thì nội dung phải giống hệt, nếu không là kênh gửi nhầm. */
    boolean sameContentAs(PostingRequest req) {
        return accountNo.equals(req.accountNo())
                && direction == req.direction()
                && amount.compareTo(req.amount()) == 0
                && currency.equals(req.currency());
    }
}
