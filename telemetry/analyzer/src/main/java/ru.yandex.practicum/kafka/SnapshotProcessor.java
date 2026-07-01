package ru.yandex.practicum.kafka;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.errors.WakeupException;
import org.springframework.stereotype.Component;
import ru.yandex.practicum.SnapshotAnalyzer;
import ru.yandex.practicum.kafka.telemetry.event.SensorsSnapshotAvro;

import java.time.Duration;
import java.util.Collections;

@Getter
@Slf4j
@Component
public class SnapshotProcessor implements Runnable {

    private final Consumer<String, SensorsSnapshotAvro> consumer;
    private final SnapshotAnalyzer analyzer;

    public SnapshotProcessor(Consumer<String, SensorsSnapshotAvro> consumer,
                             SnapshotAnalyzer analyzer) {
        this.consumer = consumer;
        this.analyzer = analyzer;
    }

    @Override
    public void run() {
        log.info("{} started. Group ID: analyzer-snapshots-group, Topics: telemetry.snapshots.v1",
                this.getClass().getSimpleName());

        try {
            while (true) {
                var records = consumer.poll(Duration.ofSeconds(5));

                if (records.isEmpty()) {
                    log.trace("No new records in snapshots, continuing poll...");
                    continue;
                }

                log.info("Polled {} records from topic {}, partitions: {}",
                        records.count(),
                        records.partitions().stream().findFirst().map(tp -> tp.topic()).orElse("unknown"),
                        records.partitions());

                boolean allProcessedSuccessfully = true;

                for (var record : records) {
                    SensorsSnapshotAvro snapshot = null;

                    try {
                        log.debug("Processing snapshot record: offset={}, partition={}, key={}",
                                record.offset(), record.partition(), record.key());

                        snapshot = record.value();

                        if (snapshot != null) {
                            // !!! ГЛАВНОЕ ИЗМЕНЕНИЕ: Ловим ошибку прямо здесь, даже если она внутри анализатора
                            try {
                                analyzer.processSnapshot(snapshot);
                                log.debug("Snapshot successfully processed: offset={}", record.offset());
                            } catch (Exception innerE) {
                                allProcessedSuccessfully = false;
                                log.error("CRITICAL: analyzer.processSnapshot FAILED for offset {} partition {}. " +
                                                "This message will be retried. Full stack trace:",
                                        record.offset(), record.partition(), innerE);
                            }
                        } else {
                            log.warn("Received null snapshot at offset {} partition {}",
                                    record.offset(), record.partition());
                            allProcessedSuccessfully = false; // Не коммитим битые сообщения
                        }
                    } catch (Exception e) {
                        allProcessedSuccessfully = false;
                        log.error("Failed to retrieve or process snapshot record at offset {} partition {}. " +
                                        "Key={}, Value class={}, Stack trace follows:",
                                record.offset(),
                                record.partition(),
                                record.key(),
                                snapshot != null ? snapshot.getClass().getName() : "null",
                                e);
                    }
                }

                // Логика коммита
                if (allProcessedSuccessfully) {
                    consumer.commitAsync((offsets, e) -> {
                        if (e != null) {
                            log.error("Async commit failed for snapshots", e);
                        } else {
                            log.info("✅ OFFSETS COMMITTED for batch size: {}", records.count());
                        }
                    });
                } else {
                    // Если хоть одна запись упала, мы НЕ делаем коммит.
                    // Kafka оставит оффсет на месте, и при следующем poll это сообщение придёт снова.
                    log.warn("⚠️ Batch had errors. Offsets NOT committed. Retrying on next poll.");
                }
            }
        } catch (WakeupException e) {
            log.info("Shutdown signal received for SnapshotProcessor");
        } catch (Exception e) {
            log.error("Unexpected error in SnapshotProcessor loop", e);
        } finally {
            try {
                consumer.close();
                log.info("Consumer closed: {}", this.getClass().getSimpleName());
            } catch (Exception e) {
                log.error("Error closing consumer", e);
            }
        }
    }

}
