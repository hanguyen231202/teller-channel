package com.tellerchannel.channel.core;

import java.math.BigDecimal;
import java.time.Instant;

/** Bút toán core trả về. */
public record CorePosting(String postingId, String referenceNo, String accountNo, String direction,
                          BigDecimal amount, String currency, BigDecimal balanceAfter, Instant postedAt) {
}
