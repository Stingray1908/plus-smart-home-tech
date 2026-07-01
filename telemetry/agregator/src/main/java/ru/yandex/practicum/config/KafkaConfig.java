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
import ru.yandex.practicum.kafka.telemetry.event.SensorsSnapshotAvro;

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
        String bootstrapServers = kafkaProperties.getBootstrapServers();
        if (bootstrapServers == null || bootstrapServers.isBlank()) {
            bootstrapServers = "localhost:9092";
        }
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);

        String groupId = kafkaProperties.getConsumer().getGroupId();
        if (groupId == null || groupId.isBlank()) {
            groupId = "smart-home-aggregator-group";
        }
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);

        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, SensorEventDeserializer.class.getName());

        String autoOffsetReset = kafkaProperties.getConsumer().getAutoOffsetReset();
        if (autoOffsetReset == null || autoOffsetReset.isBlank()) {
            autoOffsetReset = "earliest";
        }
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, autoOffsetReset);

        boolean enableAutoCommit = kafkaProperties.getConsumer().isEnableAutoCommit();
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, String.valueOf(enableAutoCommit));

        Integer maxPollIntervalMs = kafkaProperties.getConsumer().getMaxPollIntervalMs();
        if (maxPollIntervalMs == null) maxPollIntervalMs = 300000;
        props.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, String.valueOf(maxPollIntervalMs));

        Integer sessionTimeoutMs = kafkaProperties.getConsumer().getSessionTimeoutMs();
        if (sessionTimeoutMs == null) sessionTimeoutMs = 10000;
        props.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, String.valueOf(sessionTimeoutMs));

        KafkaConsumer<String, SensorEventAvro> consumer = new KafkaConsumer<>(props);

        // ПОДПИСЫВАЕМСЯ СРАЗУ ПРИ СОЗДАНИИ БИНА
        String topicName = kafkaProperties.getTopic().getSensorEvents();
        if (topicName == null || topicName.isBlank()) {
            topicName = "telemetry.sensors.v1";
        }
        consumer.subscribe(Collections.singletonList(topicName));

        return consumer;
    }


    @Bean
    public KafkaProducer<String, SensorsSnapshotAvro> kafkaProducer() {
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
