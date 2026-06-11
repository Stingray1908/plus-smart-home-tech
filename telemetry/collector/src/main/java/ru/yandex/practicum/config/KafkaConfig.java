package ru.yandex.practicum.config;

import org.apache.avro.specific.SpecificRecordBase;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;

import java.io.IOException;
import java.util.Properties;

@Configuration
public class KafkaConfig {

    @Bean
    public Properties kafkaProperties() throws IOException {
        ClassPathResource resource = new ClassPathResource("kafka-config.properties");
        return PropertiesLoaderUtils.loadProperties(resource);
    }

    @Bean
    public KafkaProducer<String, SpecificRecordBase> kafkaProducer(Properties kafkaProperties) {
        Properties props = new Properties();

        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,
                kafkaProperties.getProperty("kafka.bootstrap.servers"));
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, EventAvroSerializer.class.getName());

        String acks = kafkaProperties.getProperty("kafka.producer.acks");
        if (acks != null) {
            props.put(ProducerConfig.ACKS_CONFIG, acks);
        }

        String retries = kafkaProperties.getProperty("kafka.producer.retries");
        if (retries != null) {
            props.put(ProducerConfig.RETRIES_CONFIG, retries);
        }

        String batchSize = kafkaProperties.getProperty("kafka.producer.batch.size");
        if (batchSize != null) {
            props.put(ProducerConfig.BATCH_SIZE_CONFIG, batchSize);
        }

        return new KafkaProducer<>(props);
    }
}
