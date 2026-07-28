package ru.yandex.practicum;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import ru.yandex.practicum.config.KafkaProperties;

@ConfigurationPropertiesScan
@SpringBootApplication
@EnableConfigurationProperties(KafkaProperties.class)
public class Collector {
    public static void main(String[] args) {
        SpringApplication.run(Collector.class, args);
    }
}

