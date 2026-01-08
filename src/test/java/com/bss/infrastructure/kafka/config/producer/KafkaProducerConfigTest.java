package com.bss.infrastructure.kafka.config.producer;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = KafkaProducerConfig.class)
@ActiveProfiles("test")
class KafkaProducerConfigTest {

    @Autowired
    private ProducerFactory<String, Object> producerFactory;

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Test
    @DisplayName("ProducerFactory should have robust configuration for financial transactions")
    void producerFactory_shouldHaveRobustConfiguration() {
        // Arrange
        Map<String, Object> props = producerFactory.getConfigurationProperties();

        // Assert - Robustness
        assertThat(props.get(ProducerConfig.ACKS_CONFIG)).isEqualTo("all");
        assertThat(props.get(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG)).isEqualTo(true);
        assertThat(props.get(ProducerConfig.RETRIES_CONFIG)).isEqualTo(Integer.MAX_VALUE);
        assertThat(props.get(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION)).isEqualTo(5);

        // Assert - Serialization
        assertThat(props.get(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG)).isEqualTo(StringSerializer.class);
        assertThat(props.get(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG)).isEqualTo(JsonSerializer.class);
        assertThat(props.get(JsonSerializer.ADD_TYPE_INFO_HEADERS)).isEqualTo("true");
    }

    @Test
    @DisplayName("KafkaTemplate should be wired with the correct ProducerFactory")
    void kafkaTemplate_shouldBeWiredCorrectly() {
        // Assert
        ProducerFactory<?, ?> templateFactory = (ProducerFactory<?, ?>) ReflectionTestUtils.getField(kafkaTemplate, "producerFactory");
        assertThat(templateFactory).isSameAs(producerFactory);
    }
}
