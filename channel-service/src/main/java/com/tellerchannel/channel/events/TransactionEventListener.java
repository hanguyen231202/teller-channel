package com.tellerchannel.channel.events;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * AFTER_COMMIT: chỉ phát ra ngoài khi DB đã commit thành công.
 * Nếu phát ngay trong transaction mà sau đó rollback, hệ thống khác sẽ gửi SMS cho một giao dịch không tồn tại.
 */
@Component
public class TransactionEventListener {

    private final TransactionEventSink sink;

    public TransactionEventListener(TransactionEventSink sink) {
        this.sink = sink;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCompleted(TransactionCompletedEvent event) {
        sink.publish(event);
    }
}
