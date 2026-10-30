package com.mouss.transactionservice.messaging;

import com.mouss.transactionservice.domain.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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
     * Publishes the event only once the surrounding database transaction is committed.
     * If there is no active transaction, the event is published immediately.
     */
    public void publish(Transaction transaction) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    send(transaction);
                }
            });
        } else {
            send(transaction);
        }
    }

    private void send(Transaction transaction) {
        TransactionCreatedEvent event = new TransactionCreatedEvent(
                UUID.randomUUID(),
                "TransactionCreated",
                transaction.getId(),
                transaction.getAccountId(),
                transaction.getAmount(),
                transaction.getCurrency(),
                transaction.getCreatedAt());

        // accountId is the message key, so all events of one account keep their order.
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
