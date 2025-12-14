package com.astropay.infrastructure.kafka.config.topic;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    @Bean
    public NewTopic transactionsTopic() {
        return TopicBuilder.name("transactions")
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic accountsTopic() {
        return TopicBuilder.name("accounts")
                .partitions(1) // Apenas 1 partição é suficiente para este tipo de evento
                .replicas(1)
                .build();
    }
}
