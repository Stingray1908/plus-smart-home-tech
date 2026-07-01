package ru.yandex.practicum.config;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import ru.yandex.practicum.kafka.telemetry.event.HubEventAvro;
import ru.yandex.practicum.kafka.telemetry.event.SensorsSnapshotAvro;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaConfig {

    private static final String BOOTSTRAP_SERVERS = "localhost:9092";
    private static final String GROUP_ID_SNAPSHOTS = "analyzer-snapshots-group";
    private static final String GROUP_ID_HUBS = "analyzer-hub-events-group";

    // Фабрика для SnapshotConsumer
    @Bean
    public DefaultKafkaConsumerFactory<String, SensorsSnapshotAvro> snapshotConsumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, GROUP_ID_SNAPSHOTS);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringDeserializer");
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ru.yandex.practicum.config.SensorsSnapshotAvroDeserializer.class.getName());

        // Критично: чтобы долгая обработка не приводила к ребалансу
        props.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, "300000"); // 5 минут
        props.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, "45000");   // 45 секунд
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");

        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean
    public Consumer<String, SensorsSnapshotAvro> snapshotConsumer(
            DefaultKafkaConsumerFactory<String, SensorsSnapshotAvro> factory) {
        var consumer = factory.createConsumer();
        consumer.subscribe(Collections.singletonList("telemetry.snapshots.v1"));
        return consumer;
    }

    // Фабрика для HubEventConsumer
    @Bean
    public DefaultKafkaConsumerFactory<String, HubEventAvro> hubEventConsumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, GROUP_ID_HUBS);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringDeserializer");
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ru.yandex.practicum.config.HubEventAvroDeserializer.class.getName());

        props.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, "300000");
        props.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, "45000");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");

        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean
    public Consumer<String, HubEventAvro> hubEventConsumer(
            DefaultKafkaConsumerFactory<String, HubEventAvro> factory) {
        var consumer = factory.createConsumer();
        consumer.subscribe(Collections.singletonList("telemetry.hubs.v1"));
        return consumer;
    }
}
