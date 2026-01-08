package com.bss.infrastructure.kafka.config.topic;

import org.apache.kafka.clients.admin.NewTopic;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = KafkaTopicConfig.class)
@ActiveProfiles("test")
class KafkaTopicConfigTest {

    @Autowired
    private NewTopic transactionsTopic;

    @Autowired
    private NewTopic accountsTopic;

    @Test
    @DisplayName("Transactions topic should be configured with 3 partitions")
    void transactionsTopic_shouldHaveCorrectConfiguration() {
        assertThat(transactionsTopic).isNotNull();
        assertThat(transactionsTopic.name()).isEqualTo("transactions");
        assertThat(transactionsTopic.numPartitions()).isEqualTo(3);
        assertThat(transactionsTopic.replicationFactor()).isEqualTo((short) 1);
    }

    @Test
    @DisplayName("Accounts topic should be configured with 1 partition")
    void accountsTopic_shouldHaveCorrectConfiguration() {
        assertThat(accountsTopic).isNotNull();
        assertThat(accountsTopic.name()).isEqualTo("accounts");
        assertThat(accountsTopic.numPartitions()).isEqualTo(1);
        assertThat(accountsTopic.replicationFactor()).isEqualTo((short) 1);
    }
}
