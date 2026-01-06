package com.bss.infrastructure.kafka.config.consumer;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaConsumerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id}")
    private String groupId;

    @Bean
    public ConsumerFactory<String, Object> consumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        
        // Trust all packages
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "*");
        
        // Use type headers
        props.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, "true");

        // Explicit type mapping to ensure correct deserialization
        // This maps the simple class name (or whatever is in the header) to the full class path
        props.put(JsonDeserializer.TYPE_MAPPINGS, 
            "com.bss.application.event.transactions.TransactionEvent:com.bss.application.event.transactions.TransactionEvent," +
            "com.bss.application.event.account.AccountCreatedEvent:com.bss.application.event.account.AccountCreatedEvent"
        );

        return new DefaultKafkaConsumerFactory<>(props);
    }

    /**
     * Configures the Error Handler with Dead Letter Queue (DLQ) support.
     * If processing fails after retries, the message is sent to a topic named <original-topic>.DLT
     */
    @Bean
    public CommonErrorHandler errorHandler(KafkaTemplate<String, Object> kafkaTemplate) {
        // Retry 3 times with 1 second interval before sending to DLQ
        FixedBackOff backOff = new FixedBackOff(1000L, 3);
        
        // DeadLetterPublishingRecoverer sends the failed message to the DLT topic
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate);
        
        return new DefaultErrorHandler(recoverer, backOff);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory(CommonErrorHandler errorHandler) {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        factory.setCommonErrorHandler(errorHandler); // Apply the error handler
        return factory;
    }
}
