package ru.yandex.practicum.config;

import lombok.RequiredArgsConstructor;
import org.apache.avro.specific.SpecificRecordBase;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Properties;

@RequiredArgsConstructor
@Configuration
public class KafkaConfig {

    private final KafkaProperties kafkaProperties;

    @Bean
    public KafkaProducer<String, SpecificRecordBase> kafkaProducer() {
        Properties props = new Properties();

        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,
                kafkaProperties.getBootstrapServers());
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
                kafkaProperties.getProducer().getKeySerializer());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
                kafkaProperties.getProducer().getValueSerializer());

        if (kafkaProperties.getProducer().getAcks() != null) {
            props.put(ProducerConfig.ACKS_CONFIG,
                    kafkaProperties.getProducer().getAcks());
        }

        if (kafkaProperties.getProducer().getRetries() != null) {
            props.put(ProducerConfig.RETRIES_CONFIG,
                    kafkaProperties.getProducer().getRetries());
        }

        if (kafkaProperties.getProducer().getBatchSize() != null) {
            props.put(ProducerConfig.BATCH_SIZE_CONFIG,
                    kafkaProperties.getProducer().getBatchSize());
        }

        return new KafkaProducer<>(props);
    }
}
