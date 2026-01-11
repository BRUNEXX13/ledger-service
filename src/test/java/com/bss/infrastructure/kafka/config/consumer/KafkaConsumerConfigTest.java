package com.bss.infrastructure.kafka.config.consumer;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.backoff.FixedBackOff;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = KafkaConsumerConfig.class)
@ActiveProfiles("test")
class KafkaConsumerConfigTest {

    @MockitoBean
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private ConsumerFactory<String, Object> consumerFactory;

    @Autowired
    private CommonErrorHandler errorHandler;

    @Autowired
    private FixedBackOff errorHandlerBackOff;

    @Autowired
    private ConcurrentKafkaListenerContainerFactory<String, Object> factory;

    @Test
    @DisplayName("ConsumerFactory should have secure and correct properties")
    void consumerFactory_shouldHaveSecureAndCorrectProperties() {
        Map<String, Object> props = consumerFactory.getConfigurationProperties();

        assertThat(props.get(JsonDeserializer.TRUSTED_PACKAGES))
                .isEqualTo("com.bss.application.event.transactions,com.bss.application.event.account");

        assertThat(props.get(JsonDeserializer.USE_TYPE_INFO_HEADERS)).isEqualTo("true");
        assertThat(props.get(JsonDeserializer.TYPE_MAPPINGS)).isNull();

        assertThat(props.get(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG)).isEqualTo(StringDeserializer.class);
        assertThat(props.get(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG)).isEqualTo(JsonDeserializer.class);
    }

    @Test
    @DisplayName("BackOff bean should be configured correctly")
    void backOff_shouldBeConfiguredCorrectly() {
        assertThat(errorHandlerBackOff).isNotNull();
        assertThat(errorHandlerBackOff.getInterval()).isEqualTo(1000L);
        // The configured value is 3. Whether this means 3 retries or 3 total attempts depends on usage context,
        // but the bean property itself holds 3.
        assertThat(errorHandlerBackOff.getMaxAttempts()).isEqualTo(3);
    }

    @Test
    @DisplayName("ErrorHandler should be a DefaultErrorHandler")
    void errorHandler_shouldBeDefaultErrorHandler() {
        assertThat(errorHandler).isInstanceOf(DefaultErrorHandler.class);
    }

    @Test
    @DisplayName("KafkaListenerContainerFactory should be wired with correct ConsumerFactory and ErrorHandler")
    void kafkaListenerContainerFactory_shouldBeWiredCorrectly() {
        ConsumerFactory<?, ?> factoryConsumerFactory = (ConsumerFactory<?, ?>) ReflectionTestUtils.getField(factory, "consumerFactory");
        assertThat(factoryConsumerFactory).isSameAs(consumerFactory);

        CommonErrorHandler factoryErrorHandler = (CommonErrorHandler) ReflectionTestUtils.getField(factory, "commonErrorHandler");
        assertThat(factoryErrorHandler).isSameAs(errorHandler);
    }
}
