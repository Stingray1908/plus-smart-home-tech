package ru.yandex.practicum.kafka;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.springframework.stereotype.Component;
import ru.yandex.practicum.kafka.telemetry.event.SensorsSnapshotAvro;

import java.time.Duration;

@Slf4j
@Component
public class SnapshotProcessor implements Runnable {

    private final KafkaConsumer<String, SensorsSnapshotAvro> consumer;

    public SnapshotProcessor(KafkaConsumer<String, SensorsSnapshotAvro> consumer) {
        this.consumer = consumer;
    }

    @Override
    public void run() {
        log.info("SnapshotProcessor started");
        try {
            while (true) {
                ConsumerRecords<String, SensorsSnapshotAvro> records = consumer.poll(Duration.ofSeconds(5));

                if (records.isEmpty()) {
                    continue; // Если пусто — просто ждём дальше, это нормально
                }

                for (var record : records) {
                    SensorsSnapshotAvro snapshot = record.value();
                    if (snapshot == null) {
                        continue;
                    }
                    // Тут будет логика: взять сценарии по hubId, проверить условия, отправить gRPC
                    log.debug("Received snapshot for hub: {}", snapshot.getHubId());
                }

                // Фиксация оффсетов после успешной обработки пакета
                consumer.commitSync();
            }
        } catch (org.apache.kafka.common.errors.WakeupException e) {
            log.info("SnapshotProcessor received shutdown signal");
        } finally {
            consumer.close();
            log.info("SnapshotProcessor closed");
        }
    }

    public KafkaConsumer<String, SensorsSnapshotAvro> getConsumer() {
        return consumer;
    }

    // Метод start нужен, чтобы его можно было вызвать из main (как в ТЗ)
    public void start() {
        run();
    }
}
