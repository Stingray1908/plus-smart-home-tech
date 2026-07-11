package ru.yandex.practicum.config;

import lombok.RequiredArgsConstructor;
import org.apache.avro.specific.SpecificRecordBase;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import ru.yandex.practicum.kafka.telemetry.event.HubEventAvro;
import ru.yandex.practicum.kafka.telemetry.event.SensorsSnapshotAvro;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

@Configuration
@RequiredArgsConstructor
public class KafkaConfig {

    private final KafkaProperties kafkaProperties;

    @Bean
    public KafkaProducer<String, SpecificRecordBase> kafkaProducer() {
        Properties props = new Properties();

        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaProperties.getBootstrapServers());
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, kafkaProperties.getProducer().getKeySerializer());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, kafkaProperties.getProducer().getValueSerializer());

        if (kafkaProperties.getProducer().getAcks() != null) {
            props.put(ProducerConfig.ACKS_CONFIG, kafkaProperties.getProducer().getAcks());
        }
        if (kafkaProperties.getProducer().getRetries() != null) {
            props.put(ProducerConfig.RETRIES_CONFIG, String.valueOf(kafkaProperties.getProducer().getRetries()));
        }
        if (kafkaProperties.getProducer().getBatchSize() != null) {
            props.put(ProducerConfig.BATCH_SIZE_CONFIG, String.valueOf(kafkaProperties.getProducer().getBatchSize()));
        }

        return new KafkaProducer<>(props);
    }

    @Bean
    public Consumer<String, SensorsSnapshotAvro> snapshotConsumer() {
        Map<String, Object> props = buildCommonConsumerProps();
        props.put(ConsumerConfig.GROUP_ID_CONFIG, kafkaProperties.getGroups().getSnapshotGroup());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                ru.yandex.practicum.config.SensorsSnapshotAvroDeserializer.class.getName());

        Consumer<String, SensorsSnapshotAvro> consumer = new org.apache.kafka.clients.consumer.KafkaConsumer<>(props);
        consumer.subscribe(Collections.singletonList(kafkaProperties.getTopics().getSnapshots()));
        return consumer;
    }

    @Bean
    public Consumer<String, HubEventAvro> hubEventConsumer() {
        Map<String, Object> props = buildCommonConsumerProps();
        props.put(ConsumerConfig.GROUP_ID_CONFIG, kafkaProperties.getGroups().getHubEventsGroup());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                ru.yandex.practicum.config.HubEventAvroDeserializer.class.getName());

        Consumer<String, HubEventAvro> consumer = new org.apache.kafka.clients.consumer.KafkaConsumer<>(props);
        consumer.subscribe(Collections.singletonList(kafkaProperties.getTopics().getHubs()));
        return consumer;
    }

    private Map<String, Object> buildCommonConsumerProps() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaProperties.getBootstrapServers());
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG,
                kafkaProperties.getConsumer().getMaxPollIntervalMs());
        props.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG,
                kafkaProperties.getConsumer().getSessionTimeoutMs());
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG,
                kafkaProperties.getConsumer().isEnableAutoCommit());

        if (kafkaProperties.getConsumer().getAutoOffsetReset() != null) {
            props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
                    kafkaProperties.getConsumer().getAutoOffsetReset());
        }

        return props;
    }
}
