//package com.astropay.infrastructure.kafka.config.consumer;
//
//import org.apache.kafka.clients.consumer.ConsumerConfig;
//import org.apache.kafka.common.TopicPartition;
//import org.apache.kafka.common.serialization.StringDeserializer;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
//import org.springframework.kafka.core.ConsumerFactory;
//import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
//import org.springframework.kafka.core.KafkaTemplate;
//import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
//import org.springframework.kafka.listener.DefaultErrorHandler;
//import org.springframework.kafka.support.serializer.JsonDeserializer;
//import org.springframework.util.backoff.ExponentialBackOff;
//
//import java.util.HashMap;
//import java.util.Map;
//
//@Configuration
//public class KafkaConsumerConfig {
//
//    @Value("${spring.kafka.bootstrap-servers}")
//    private String bootstrapServers;
//
//    @Value("${spring.kafka.consumer.group-id}")
//    private String groupId;
//
//    @Bean
//    public ConsumerFactory<String, Object> consumerFactory() {
//        Map<String, Object> props = new HashMap<>();
//        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
//        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
//
//        JsonDeserializer<Object> deserializer = new JsonDeserializer<>();
//        deserializer.addTrustedPackages("*");
//
//        return new DefaultKafkaConsumerFactory<>(props, new StringDeserializer(), deserializer);
//    }
//
//    @Bean
//    public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory(
//            ConsumerFactory<String, Object> consumerFactory,
//            KafkaTemplate<String, Object> kafkaTemplate) {
//
//        ConcurrentKafkaListenerContainerFactory<String, Object> factory = new ConcurrentKafkaListenerContainerFactory<>();
//        factory.setConsumerFactory(consumerFactory);
//        factory.setCommonErrorHandler(errorHandler(kafkaTemplate));
//        return factory;
//    }
//
//    @Bean
//    public DefaultErrorHandler errorHandler(KafkaTemplate<String, Object> template) {
//        // Envia para um tópico com sufixo .DLT (Dead Letter Topic) em caso de falha total
//        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(template,
//            (record, exception) -> new TopicPartition(record.topic() + ".DLT", record.partition()));
//
//        // Configuração de Backoff Exponencial
//        // Inicia com 1 segundo de espera
//        // Multiplica por 2 a cada tentativa (1s -> 2s -> 4s)
//        // Tenta no máximo 3 vezes antes de desistir e mandar para a DLQ
//        ExponentialBackOff backOff = new ExponentialBackOff(1000L, 2.0);
//        backOff.setMaxElapsedTime(10000L); // Tempo máximo total de tentativas
//        backOff.setMaxInterval(5000L); // Tempo máximo de espera entre tentativas
//
//        return new DefaultErrorHandler(recoverer, backOff);
//    }
//}
