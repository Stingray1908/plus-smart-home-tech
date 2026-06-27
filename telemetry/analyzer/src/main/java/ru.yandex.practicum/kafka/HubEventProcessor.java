package ru.yandex.practicum.kafka;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.springframework.stereotype.Component;
import ru.yandex.practicum.kafka.telemetry.event.HubEventAvro;

import java.time.Duration;

@Getter
@Slf4j
@Component
public class HubEventProcessor implements Runnable {

    private final KafkaConsumer<String, HubEventAvro> consumer;

    public HubEventProcessor(KafkaConsumer<String, HubEventAvro> consumer) {
        this.consumer = consumer;
    }

    @Override
    public void run() {
        log.info("HubEventProcessor started");
        try {
            while (true) {
                ConsumerRecords<String, HubEventAvro> records = consumer.poll(Duration.ofSeconds(5));

                if (records.isEmpty()) {
                    continue;
                }

                boolean allProcessedSuccessfully = true;

                for (var record : records) {
                    HubEventAvro event = record.value();
                    if (event == null) {
                        continue;
                    }

                    try {
                        // ТВОЯ ЛОГИКА ОБРАБОТКИ СОБЫТИЯ
                        processEvent(event);

                        log.debug("Processed hub event, hubId={}", event.getHubId());
                    } catch (Exception e) {
                        // ОШИБКА ОБРАБОТКИ
                        allProcessedSuccessfully = false;
                        log.error("Failed to process hub event at offset {} partition {}. Will retry on next poll.",
                                record.offset(), record.partition(), e);
                        // НЕ делаем return и НЕ делаем break!
                        // Мы должны попробовать обработать остальные записи в этом батче,
                        // но флаг allProcessedSuccessfully уже false.
                    }
                }

                // ГЛАВНОЕ ИЗМЕНЕНИЕ:
                if (allProcessedSuccessfully) {
                    consumer.commitSync(); // Фиксируем оффсеты только если ВСЁ прошло успешно
                    log.trace("Offsets committed successfully for batch size: {}", records.count());
                } else {
                    // Если была хоть одна ошибка, мы НЕ делаем commitSync().
                    // Kafka оставит оффсеты на месте. При следующем poll или рестарте
                    // эти сообщения придут снова.
                    log.warn("Batch contained errors. Offsets NOT committed. Retrying on next iteration.");
                }
            }
        } catch (org.apache.kafka.common.errors.WakeupException e) {
            log.info("HubEventProcessor received shutdown signal");
        } finally {
            consumer.close();
            log.info("HubEventProcessor closed");
        }
    }

    // Вынес логику обработки в отдельный метод, чтобы try-catch был чистым
    private void processEvent(HubEventAvro event) {
        // Здесь твой старый код:
        // if (event.getType() == ADD_SENSOR) ...
        // throw new RuntimeException если что-то не так
    }
}
