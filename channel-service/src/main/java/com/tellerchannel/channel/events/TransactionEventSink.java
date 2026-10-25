package com.tellerchannel.channel.events;

/** Nơi sự kiện được đẩy ra ngoài. Có 2 bản: Kafka (thật) và Logging (khi tắt Kafka). */
public interface TransactionEventSink {

    void publish(TransactionCompletedEvent event);
}
