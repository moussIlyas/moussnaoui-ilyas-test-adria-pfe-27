package com.mouss.transactionservice.messaging;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    @Bean
    NewTopic transactionsCreatedTopic() {
        return TopicBuilder.name(TransactionEventPublisher.TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }
}
