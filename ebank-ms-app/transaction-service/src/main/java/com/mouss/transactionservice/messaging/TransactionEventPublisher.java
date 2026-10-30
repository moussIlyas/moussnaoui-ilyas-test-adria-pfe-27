package com.mouss.transactionservice.messaging;

import com.mouss.transactionservice.domain.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class TransactionEventPublisher {

    public static final String TOPIC = "bank.transactions.created";

    private static final Logger log = LoggerFactory.getLogger(TransactionEventPublisher.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public TransactionEventPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * Publishes only after the transaction has been persisted. accountId is used as the message key.
     */
    public void publish(Transaction transaction) {
        TransactionCreatedEvent event = new TransactionCreatedEvent(
                UUID.randomUUID(),
                "TransactionCreated",
                transaction.getId(),
                transaction.getAccountId(),
                transaction.getAmount(),
                transaction.getCurrency(),
                transaction.getCreatedAt());

        kafkaTemplate.send(TOPIC, transaction.getAccountId().toString(), event)
                .whenComplete((result, failure) -> {
                    if (failure != null) {
                        log.error("Failed to publish {} for transaction {} (account {})",
                                TOPIC, transaction.getId(), transaction.getAccountId(), failure);
                    } else {
                        log.info("Published {} for transaction {} (account {}) to partition {} offset {}",
                                TOPIC,
                                transaction.getId(),
                                transaction.getAccountId(),
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());
                    }
                });
    }
}
