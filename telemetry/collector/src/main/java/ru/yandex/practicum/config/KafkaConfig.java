package ru.yandex.practicum.config;

import org.apache.avro.specific.SpecificRecordBase;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Properties;

@Configuration
public class KafkaConfig {

    private final KafkaProperties kafkaProperties;

    public KafkaConfig(KafkaProperties kafkaProperties) {
        this.kafkaProperties = kafkaProperties;
    }

    @Bean
    public KafkaProducer<String, SpecificRecordBase> kafkaProducer() {
        Properties props = new Properties();

        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,
                kafkaProperties.getBootstrapServers());

        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
                kafkaProperties.getProducer().getKeySerializer());

        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
                kafkaProperties.getProducer().getValueSerializer());

        props.put(ProducerConfig.ACKS_CONFIG, String.valueOf(kafkaProperties.getProducer().getAcks()));
        props.put(ProducerConfig.RETRIES_CONFIG, String.valueOf(kafkaProperties.getProducer().getRetries()));
        props.put(ProducerConfig.BATCH_SIZE_CONFIG, String.valueOf(kafkaProperties.getProducer().getBatchSize()));

        return new KafkaProducer<>(props);
    }
}
