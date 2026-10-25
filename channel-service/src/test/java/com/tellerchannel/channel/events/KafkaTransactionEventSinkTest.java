package com.tellerchannel.channel.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.tellerchannel.channel.transaction.TransactionStatus;
import com.tellerchannel.channel.transaction.TransactionType;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class KafkaTransactionEventSinkTest {

    @Test
    @SuppressWarnings("unchecked")
    void sendsJsonKeyedByTransactionRef() {
        KafkaTemplate<String, String> template = mock(KafkaTemplate.class);
        when(template.send(anyString(), anyString(), anyString())).thenReturn(new CompletableFuture<>());
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        KafkaTransactionEventSink sink = new KafkaTransactionEventSink(template, mapper, "teller.transaction.completed");

        sink.publish(new TransactionCompletedEvent("TC1", TransactionType.DEPOSIT, "23122002", new BigDecimal("100000"),
                "VND", TransactionStatus.SUCCESS, "PST1", null, "gdv01", "ksv01", Instant.now()));

        verify(template).send(eq("teller.transaction.completed"), eq("TC1"), contains("\"status\":\"SUCCESS\""));
    }
}
