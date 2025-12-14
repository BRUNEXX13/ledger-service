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
                .partitions(3) // Permite até 3 consumidores paralelos no futuro
                .replicas(1)   // 1 réplica pois temos apenas 1 broker local
                .build();
    }
}
