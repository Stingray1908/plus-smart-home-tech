package ru.yandex.practicum.config;

import org.apache.avro.specific.SpecificRecordBase;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.yandex.practicum.kafka.telemetry.event.SensorEventAvro;

import java.util.Collections;
import java.util.Properties;

@Configuration
public class KafkaConfig {

    @Bean
    public KafkaConsumer<String, SensorEventAvro> kafkaConsumer() {
        Properties props = new Properties();

        // Жёсткие значения — никаких null из конфига
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "smart-home-aggregator-group");

        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ru.yandex.practicum.config.SensorEventDeserializer.class.getName());

        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        props.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, "300000");
        props.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, "10000");
        props.put(ConsumerConfig.FETCH_MIN_BYTES_CONFIG, "1");
        props.put(ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG, "500");

        KafkaConsumer<String, SensorEventAvro> consumer = new KafkaConsumer<>(props);
        consumer.subscribe(Collections.singletonList("telemetry.sensors.v1"));
        return consumer;
    }

    @Bean
    public KafkaProducer<String, SpecificRecordBase> kafkaProducer() {
        Properties props = new Properties();
        props.put("bootstrap.servers", "localhost:9092");
        props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        props.put("value.serializer", "ru.yandex.practicum.config.EventAvroSerializer");
        props.put("acks", "1");
        props.put("retries", "3");
        props.put("batch.size", "16384");

        return new KafkaProducer<>(props);
    }
}
