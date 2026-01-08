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
