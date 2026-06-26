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
    private Consumer consumer = new Consumer();
    private Producer producer = new Producer();

    @Getter
    @Setter
    public static class Topic {
        private String sensorEvents;
        private String snapshots;
    }

    @Getter
    @Setter
    public static class Consumer {
        private String groupId;
        private String autoOffsetReset;
        private boolean enableAutoCommit;
        private Integer maxPollIntervalMs;
        private Integer sessionTimeoutMs;
        private Integer fetchMinBytes;
        private Integer fetchMaxWaitMs;
    }

    @Getter
    @Setter
    public static class Producer {
        private String acks;
        private String retries;
        private String batchSize;
        private String keySerializer;
        private String valueSerializer;
    }
}
