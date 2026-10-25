package com.tellerchannel.channel.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "teller.events.kafka.enabled", havingValue = "false", matchIfMissing = true)
public class LoggingTransactionEventSink implements TransactionEventSink {

    private static final Logger log = LoggerFactory.getLogger(LoggingTransactionEventSink.class);

    @Override
    public void publish(TransactionCompletedEvent event) {
        log.info("Event (Kafka disabled): {} {} {}", event.transactionRef(), event.status(), event.amount());
    }
}
