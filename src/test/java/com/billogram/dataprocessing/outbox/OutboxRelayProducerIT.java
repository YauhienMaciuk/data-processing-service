package com.billogram.dataprocessing.outbox;

import org.apache.kafka.clients.producer.ProducerConfig;
import com.billogram.dataprocessing.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The relay waits for each message's confirmation before sending the next, so its throughput depends on the producer
 * sending right away instead of waiting to fill a batch (see {@code application.properties}).
 */
@IntegrationTest
class OutboxRelayProducerIT {

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Test
    void producerSendsWithoutWaitingToFillBatches() {
        Object linger = kafkaTemplate.getProducerFactory().getConfigurationProperties().get(ProducerConfig.LINGER_MS_CONFIG);

        assertEquals("0", String.valueOf(linger));
    }
}
