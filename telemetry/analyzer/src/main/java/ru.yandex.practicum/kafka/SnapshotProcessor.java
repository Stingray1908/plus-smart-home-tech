package ru.yandex.practicum.kafka;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.errors.WakeupException;
import org.springframework.stereotype.Component;
import ru.yandex.practicum.SnapshotAnalyzer;
import ru.yandex.practicum.kafka.telemetry.event.SensorsSnapshotAvro;

import java.time.Duration;

@Getter
@Slf4j
@Component
public class SnapshotProcessor implements Runnable {

    private final KafkaConsumer<String, SensorsSnapshotAvro> consumer;
    private final SnapshotAnalyzer analyzer;

    public SnapshotProcessor(KafkaConsumer<String, SensorsSnapshotAvro> consumer,
                             SnapshotAnalyzer analyzer) {
        this.consumer = consumer;
        this.analyzer = analyzer;
    }

    @Override
    public void run() {
        log.info("SnapshotProcessor started");
        try {
            while (true) {
                ConsumerRecords<String, SensorsSnapshotAvro> records = consumer.poll(Duration.ofSeconds(5));

                if (records.isEmpty()) {
                    continue;
                }

                boolean batchSuccess = true;

                for (var record : records) {
                    try {
                        SensorsSnapshotAvro snapshot = record.value();
                        if (snapshot != null) {
                            analyzer.processSnapshot(snapshot);
                        }
                    } catch (Exception e) {
                        batchSuccess = false;
                        log.error("Error processing snapshot at offset {} partition {}: {}",
                                record.offset(), record.partition(), e.getMessage(), e);
                    }
                }

                if (batchSuccess) {
                    consumer.commitSync();
                    log.trace("Offsets committed for batch size: {}", records.count());
                } else {
                    // Не делаем commitSync — Kafka оставит оффсеты, сообщения придут снова
                    log.warn("Batch contained errors. Offsets NOT committed. Retrying on next poll.");
                }
            }
        } catch (WakeupException e) {
            // Это нормальный путь остановки консьюмера (вызван consumer.wakeup())
            log.info("SnapshotProcessor received wakeup signal. Shutting down gracefully.");
        } catch (Exception e) {
            log.error("Unexpected error in SnapshotProcessor loop", e);
            // Здесь можно добавить логику повторной попытки или остановки сервиса
        } finally {
            consumer.close();
            log.info("SnapshotProcessor closed");
        }
    }
}
