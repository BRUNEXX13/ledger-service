package com.bss.infrastructure.kafka;

import com.bss.application.event.transactions.TransactionEvent;
import com.bss.infrastructure.kafka.config.consumer.KafkaConsumerConfig;
import com.bss.infrastructure.kafka.config.producer.KafkaProducerConfig;
import com.bss.infrastructure.kafka.config.topic.KafkaTopicConfig;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.KafkaMessageListenerContainer;
import org.springframework.kafka.listener.MessageListener;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.ContainerTestUtils;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {
    KafkaAutoConfiguration.class,
    KafkaConsumerConfig.class,
    KafkaProducerConfig.class,
    KafkaTopicConfig.class
})
@EmbeddedKafka(partitions = 1, topics = {"test-transactions"})
@ActiveProfiles("test")
class KafkaIntegrationTest {

    @Autowired
    private EmbeddedKafkaBroker embeddedKafkaBroker;

    @Test
    void testSendAndReceiveTransactionEvent() throws Exception {
        // 1. Configure Consumer
        Map<String, Object> consumerProps = KafkaTestUtils.consumerProps("test-group", "true", embeddedKafkaBroker);
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        consumerProps.put(JsonDeserializer.TRUSTED_PACKAGES, "*");
        
        DefaultKafkaConsumerFactory<String, TransactionEvent> consumerFactory = new DefaultKafkaConsumerFactory<>(consumerProps);
        ContainerProperties containerProperties = new ContainerProperties("test-transactions");
        
        BlockingQueue<ConsumerRecord<String, TransactionEvent>> records = new LinkedBlockingQueue<>();
        KafkaMessageListenerContainer<String, TransactionEvent> container = new KafkaMessageListenerContainer<>(consumerFactory, containerProperties);
        container.setupMessageListener((MessageListener<String, TransactionEvent>) records::add);
        container.start();
        
        ContainerTestUtils.waitForAssignment(container, embeddedKafkaBroker.getPartitionsPerTopic());

        // 2. Configure Producer
        Map<String, Object> producerProps = KafkaTestUtils.producerProps(embeddedKafkaBroker);
        producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        
        DefaultKafkaProducerFactory<String, TransactionEvent> producerFactory = new DefaultKafkaProducerFactory<>(producerProps);
        KafkaTemplate<String, TransactionEvent> template = new KafkaTemplate<>(producerFactory);

        // 3. Send Message
        TransactionEvent event = new TransactionEvent(
                1L, 10L, 20L, BigDecimal.TEN, LocalDateTime.now(), UUID.randomUUID()
        );
        template.send("test-transactions", event);

        // 4. Verify Receipt
        ConsumerRecord<String, TransactionEvent> received = records.poll(10, TimeUnit.SECONDS);
        assertThat(received).isNotNull();
        assertThat(received.value().getAmount()).isEqualByComparingTo(BigDecimal.TEN);
        assertThat(received.value().getIdempotencyKey()).isEqualTo(event.getIdempotencyKey());
        
        container.stop();
    }
}
