package ru.yandex.practicum.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "kafka")
public class KafkaProperties {
    private String bootstrapServers;
    private Consumer consumer = new Consumer();
    private Producer producer = new Producer();
    private Topics topics = new Topics();
    private Groups groups = new Groups();

    @Data
    public static class Producer {
        private String keySerializer;
        private String valueSerializer;
        private String acks;
        private Integer retries;
        private Integer batchSize;
    }

    @Data
    public static class Consumer {
        private String autoOffsetReset = "earliest";
        private int maxPollIntervalMs = 300000;
        private int sessionTimeoutMs = 45000;
        private boolean enableAutoCommit = false;
    }

    @Data
    public static class Topics {
        private String sensorEvents;
        private String snapshots;
        private String hubs;
    }

    @Data
    public static class Groups {
        private String snapshotGroup = "analyzer-snapshots-group";
        private String hubEventsGroup = "analyzer-hub-events-group";
    }
}
