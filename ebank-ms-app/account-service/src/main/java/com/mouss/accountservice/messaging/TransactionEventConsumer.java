package com.mouss.accountservice.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumer required by Part F: it only logs the received event and never writes banking data.
 */
@Component
public class TransactionEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(TransactionEventConsumer.class);

    @KafkaListener(
            topics = "${app.kafka.transactions-topic:bank.transactions.created}",
            groupId = "${spring.kafka.consumer.group-id:account-service}")
    public void onTransactionCreated(TransactionCreatedEvent event) {
        log.info("Kafka event received: eventType={} eventId={} transactionId={} accountId={} amount={} {} occurredAt={}",
                event.eventType(),
                event.eventId(),
                event.transactionId(),
                event.accountId(),
                event.amount(),
                event.currency(),
                event.occurredAt());
    }
}
