package ru.yandex.practicum.config;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.yandex.practicum.kafka.telemetry.event.SensorEventAvro;
import ru.yandex.practicum.kafka.telemetry.event.SensorsSnapshotAvro;

import java.util.Collections;
import java.util.Properties;

@Configuration
public class KafkaConfig {

    private final KafkaProperties kafkaProperties;

    public KafkaConfig(KafkaProperties kafkaProperties) {
        this.kafkaProperties = kafkaProperties;
    }

    @Bean
    public KafkaConsumer<String, SensorEventAvro> kafkaConsumer() {
        var props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaProperties.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, kafkaProperties.getConsumer().getGroupId());
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, SensorEventDeserializer.class.getName());

        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, kafkaProperties.getConsumer().getAutoOffsetReset());
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, String.valueOf(kafkaProperties.getConsumer().isEnableAutoCommit()));

        props.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG,
                String.valueOf(kafkaProperties.getConsumer().getMaxPollIntervalMs()));
        props.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG,
                String.valueOf(kafkaProperties.getConsumer().getSessionTimeoutMs()));

        props.put(ConsumerConfig.FETCH_MIN_BYTES_CONFIG,
                String.valueOf(kafkaProperties.getConsumer().getFetchMinBytes()));
        props.put(ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG,
                String.valueOf(kafkaProperties.getConsumer().getFetchMaxWaitMs()));

        KafkaConsumer<String, SensorEventAvro> consumer = new KafkaConsumer<>(props);
        String topicName = kafkaProperties.getTopic().getSensorEvents();
        consumer.subscribe(Collections.singletonList(topicName));
        return consumer;
    }

    @Bean
    public KafkaProducer<String, SensorsSnapshotAvro> kafkaProducer() {
        var props = new Properties();

        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaProperties.getBootstrapServers());

        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, kafkaProperties.getProducer().getKeySerializer());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, kafkaProperties.getProducer().getValueSerializer());

        props.put(ProducerConfig.ACKS_CONFIG, String.valueOf(kafkaProperties.getProducer().getAcks()));
        props.put(ProducerConfig.RETRIES_CONFIG, String.valueOf(kafkaProperties.getProducer().getRetries()));
        props.put(ProducerConfig.BATCH_SIZE_CONFIG, String.valueOf(kafkaProperties.getProducer().getBatchSize()));

        return new KafkaProducer<>(props);
    }
}
