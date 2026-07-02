package ru.yandex.practicum.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "kafka")
@Getter
@Setter
public class KafkaProperties {
    private String bootstrapServers;
    private Topic topic = new Topic();
    private Producer producer = new Producer();

    @Getter
    @Setter
    public static class Topic {
        private String hubEvents;
        private String sensorEvents;
    }

    @Getter
    @Setter
    public static class Producer {
        private int acks;
        private int retries;
        private int batchSize;
        private String keySerializer;
        private String valueSerializer;
    }
}
