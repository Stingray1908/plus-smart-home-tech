package ru.yandex.practicum.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import java.util.Map;

@Data
@ConfigurationProperties(prefix = "kafka")
public class KafkaProperties {
    private String bootstrapServers;

    private Producer producer = new Producer();

    @Data
    public static class Producer {
        private String keySerializer;
        private String valueSerializer;
        private String acks;
        private Integer retries;
        private Integer batchSize;
    }

    private Consumer consumer = new Consumer();

    @Data
    public static class Consumer {
        private String keyDeserializer;
        private String valueDeserializer;
        private int maxPollIntervalMs = 300000;      // 5 минут
        private int sessionTimeoutMs = 45000;       // 45 секунд
        private boolean enableAutoCommit = false;
    }
}
