package com.tellerchannel.channel.events;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Key = transactionRef: mọi sự kiện của cùng giao dịch vào cùng partition, nên giữ đúng thứ tự.
 */
@Component
@ConditionalOnProperty(name = "teller.events.kafka.enabled", havingValue = "true")
public class KafkaTransactionEventSink implements TransactionEventSink {

    private static final Logger log = LoggerFactory.getLogger(KafkaTransactionEventSink.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final String topic;

    public KafkaTransactionEventSink(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper,
                                     @Value("${teller.events.topic}") String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.topic = topic;
    }

    @Override
    public void publish(TransactionCompletedEvent event) {
        String payload;
        try {
            payload = objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize event " + event.transactionRef(), e);
        }
        kafkaTemplate.send(topic, event.transactionRef(), payload).whenComplete((result, ex) -> {
            if (ex != null) {
                // Mất sự kiện ở đây KHÔNG ảnh hưởng tiền (DB đã commit), nhưng khách có thể không nhận SMS.
                // Muốn không bao giờ mất: Outbox pattern (xem STEPS.md, step 12).
                log.error("Failed to publish event for {}: {}", event.transactionRef(), ex.toString());
            } else {
                log.info("Published event {} {} to {}-{}@{}", event.transactionRef(), event.status(),
                        result.getRecordMetadata().topic(), result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            }
        });
    }
}
