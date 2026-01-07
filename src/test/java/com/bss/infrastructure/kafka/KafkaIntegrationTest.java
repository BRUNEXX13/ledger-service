package com.bss.infrastructure.kafka;

import com.bss.application.event.transactions.TransactionEvent;
import com.bss.infrastructure.kafka.config.consumer.KafkaConsumerConfig;
import com.bss.infrastructure.kafka.config.producer.KafkaProducerConfig;
import com.bss.infrastructure.kafka.config.topic.KafkaTopicConfig;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.KafkaMessageListenerContainer;
import org.springframework.kafka.listener.MessageListener;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.ContainerTestUtils;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.ActiveProfiles;
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
@SpringBootTest(classes = {
    KafkaAutoConfiguration.class,
    KafkaConsumerConfig.class,
    KafkaProducerConfig.class,
    KafkaTopicConfig.class
})
@EmbeddedKafka(partitions = 1, topics = {"transactions"})
@ActiveProfiles("test")
class KafkaIntegrationTest {

    @Autowired
    private EmbeddedKafkaBroker embeddedKafkaBroker;

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Test
    void testSendAndReceiveTransactionEvent() throws Exception {
        // 1. Configure a Test Consumer
        Map<String, Object> consumerProps = KafkaTestUtils.consumerProps("test-group", "true", embeddedKafkaBroker);
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        consumerProps.put(JsonDeserializer.TRUSTED_PACKAGES, "*");
        
        DefaultKafkaConsumerFactory<String, TransactionEvent> consumerFactory = new DefaultKafkaConsumerFactory<>(consumerProps);
        ContainerProperties containerProperties = new ContainerProperties("transactions");
        
        BlockingQueue<ConsumerRecord<String, TransactionEvent>> records = new LinkedBlockingQueue<>();
        KafkaMessageListenerContainer<String, TransactionEvent> container = new KafkaMessageListenerContainer<>(consumerFactory, containerProperties);
        container.setupMessageListener((MessageListener<String, TransactionEvent>) records::add);
        container.start();
        
        // FIX: The application's KafkaTopicConfig creates the 'transactions' topic with 3 partitions.
        // The @EmbeddedKafka annotation creates it with 1 partition, but the application context loading
        // might override or recreate it, or the test utility expects the number of partitions assigned to match.
        // Since KafkaTopicConfig defines 3 partitions, we should expect 3 partitions here.
        // Alternatively, we can rely on the fact that EmbeddedKafkaBroker handles this.
        // The error "Expected 1 but got 3 partitions" suggests the topic has 3 partitions (from KafkaTopicConfig)
        // but we are waiting for assignment on a container that might only be assigned 1 if not configured correctly,
        // OR we passed '1' to waitForAssignment but there are 3.
        
        // ContainerTestUtils.waitForAssignment waits until the container has been assigned 'partitions' number of partitions.
        // Since we have 1 consumer in the group and the topic has 3 partitions, this consumer should get all 3.
        ContainerTestUtils.waitForAssignment(container, 3);

        // 2. Send Message
        TransactionEvent event = new TransactionEvent(
                1L, 10L, 20L, BigDecimal.TEN, LocalDateTime.now(), UUID.randomUUID()
        );
        kafkaTemplate.send("transactions", event);

        // 3. Verify Receipt
        ConsumerRecord<String, TransactionEvent> received = records.poll(10, TimeUnit.SECONDS);
        assertThat(received).isNotNull();
        assertThat(received.value().getAmount()).isEqualByComparingTo(BigDecimal.TEN);
        assertThat(received.value().getIdempotencyKey()).isEqualTo(event.getIdempotencyKey());
        
        container.stop();
    }
}
