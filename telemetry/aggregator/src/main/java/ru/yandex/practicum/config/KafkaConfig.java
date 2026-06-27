package ru.yandex.practicum.config;

import org.apache.avro.specific.SpecificRecordBase;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.yandex.practicum.kafka.telemetry.event.SensorEventAvro;

import java.util.Collections;
import java.util.Properties;

@Configuration
public class KafkaConfig {

    private KafkaProperties kafkaProperties;

    @Autowired
    public KafkaConfig(KafkaProperties kafkaProperties) {
        this.kafkaProperties = kafkaProperties;
    }
    @Bean
    public KafkaConsumer<String, SensorEventAvro> kafkaConsumer() {
        Properties props = new Properties();

        // Bootstrap servers (гарантированно строка)
        String bootstrapServers = kafkaProperties.getBootstrapServers();
        if (bootstrapServers == null || bootstrapServers.isBlank()) {
            bootstrapServers = "localhost:9092";
        }
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);

        // Group ID
        String groupId = kafkaProperties.getConsumer().getGroupId();
        if (groupId == null || groupId.isBlank()) {
            groupId = "smart-home-aggregator-group";
        }
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);

        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ru.yandex.practicum.config.SensorEventDeserializer.class.getName());

        // AUTO_OFFSET_RESET: строка
        String autoOffsetReset = kafkaProperties.getConsumer().getAutoOffsetReset();
        if (autoOffsetReset == null || autoOffsetReset.isBlank()) {
            autoOffsetReset = "earliest";
        }
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, autoOffsetReset);

        // ENABLE_AUTO_COMMIT: boolean -> String
        boolean enableAutoCommit = kafkaProperties.getConsumer().isEnableAutoCommit();
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, String.valueOf(enableAutoCommit));

        // MAX_POLL_INTERVAL_MS: int -> String
        Integer maxPollIntervalMs = kafkaProperties.getConsumer().getMaxPollIntervalMs();
        if (maxPollIntervalMs == null) {
            maxPollIntervalMs = 300000;
        }
        props.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, String.valueOf(maxPollIntervalMs));

        // SESSION_TIMEOUT_MS: int -> String
        Integer sessionTimeoutMs = kafkaProperties.getConsumer().getSessionTimeoutMs();
        if (sessionTimeoutMs == null) {
            sessionTimeoutMs = 10000;
        }
        props.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, String.valueOf(sessionTimeoutMs));

        // FETCH_MIN_BYTES: int -> String
        Integer fetchMinBytes = kafkaProperties.getConsumer().getFetchMinBytes();
        if (fetchMinBytes == null) {
            fetchMinBytes = 1;
        }
        props.put(ConsumerConfig.FETCH_MIN_BYTES_CONFIG, String.valueOf(fetchMinBytes));

        // FETCH_MAX_WAIT_MS: int -> String
        Integer fetchMaxWaitMs = kafkaProperties.getConsumer().getFetchMaxWaitMs();
        if (fetchMaxWaitMs == null) {
            fetchMaxWaitMs = 500;
        }
        props.put(ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG, String.valueOf(fetchMaxWaitMs));

        KafkaConsumer<String, SensorEventAvro> consumer = new KafkaConsumer<>(props);
        consumer.subscribe(Collections.singletonList(
                kafkaProperties.getTopic().getSensorEvents() != null
                        ? kafkaProperties.getTopic().getSensorEvents()
                        : "telemetry.sensors.v1"
        ));
        return consumer;
    }

    @Bean
    public KafkaProducer<String, SpecificRecordBase> kafkaProducer() {
        Properties props = new Properties();

        String bootstrapServers = kafkaProperties.getBootstrapServers();
        if (bootstrapServers == null || bootstrapServers.isBlank()) {
            bootstrapServers = "localhost:9092";
        }
        props.put("bootstrap.servers", bootstrapServers);

        String keySerializer = kafkaProperties.getProducer().getKeySerializer();
        if (keySerializer == null || keySerializer.isBlank()) {
            keySerializer = "org.apache.kafka.common.serialization.StringSerializer";
        }
        props.put("key.serializer", keySerializer);

        String valueSerializer = kafkaProperties.getProducer().getValueSerializer();
        if (valueSerializer == null || valueSerializer.isBlank()) {
            valueSerializer = "ru.yandex.practicum.config.EventAvroSerializer";
        }
        props.put("value.serializer", valueSerializer);

        String acks = kafkaProperties.getProducer().getAcks();
        if (acks != null) {
            props.put("acks", acks);
        }

        String retries = kafkaProperties.getProducer().getRetries();
        if (retries != null) {
            props.put("retries", retries);
        }

        String batchSize = kafkaProperties.getProducer().getBatchSize();
        if (batchSize != null) {
            props.put("batch.size", batchSize);
        }

        return new KafkaProducer<>(props);
    }
}
