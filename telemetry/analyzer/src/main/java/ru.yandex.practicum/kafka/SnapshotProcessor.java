package ru.yandex.practicum.kafka;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.springframework.stereotype.Component;
import ru.yandex.practicum.SnapshotAnalyzer;
import ru.yandex.practicum.kafka.telemetry.event.SensorsSnapshotAvro;

import java.time.Duration;

@Slf4j
@Component
public class SnapshotProcessor implements Runnable {
    @Getter
    private final KafkaConsumer<String, SensorsSnapshotAvro> consumer;
    private final SnapshotAnalyzer analyzer;

    public SnapshotProcessor(KafkaConsumer<String, SensorsSnapshotAvro> consumer,
                             SnapshotAnalyzer analyzer) {
        this.consumer = consumer;
        this.analyzer = analyzer;
    }

    @Override
    public void run() {
        while (true) {
            var records = consumer.poll(Duration.ofSeconds(5));
            for (var record : records) {
                SensorsSnapshotAvro snapshot = record.value();
                if (snapshot != null) {
                    analyzer.processSnapshot(snapshot);
                }
            }
            consumer.commitSync();
        }
    }

    public void start() {
        run();
    }
}
