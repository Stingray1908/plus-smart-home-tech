package ru.yandex.practicum.kafka;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.springframework.stereotype.Component;
import ru.yandex.practicum.kafka.telemetry.event.HubEventAvro;

import java.time.Duration;

@Slf4j
@Component
public class HubEventProcessor implements Runnable {

    private final KafkaConsumer<String, HubEventAvro> consumer; // HubEventAvro — тип события о хабе/структуре

    public HubEventProcessor(KafkaConsumer<String, HubEventAvro> consumer) {
        this.consumer = consumer;
    }

    @Override
    public void run() {
        log.info("HubEventProcessor started");
        try {
            while (true) {
                ConsumerRecords<String, HubEventAvro> records = consumer.poll(Duration.ofSeconds(5));

                for (var record : records) {
                    HubEventAvro event = record.value();
                    if (event == null) {
                        continue;
                    }

                    // Тут логика:
                    // если event.getType() == ADD_SENSOR -> sensorRepository.save(...)
                    // если event.getType() == DELETE_SCENARIO -> scenarioRepository.deleteById(...)
                    log.debug("Received hub event, hubId={}", event.getHubId());
                }

                consumer.commitSync();
            }
        } catch (org.apache.kafka.common.errors.WakeupException e) {
            log.info("HubEventProcessor received shutdown signal");
        } finally {
            consumer.close();
            log.info("HubEventProcessor closed");
        }
    }

    public KafkaConsumer<String, HubEventAvro> getConsumer() {
        return consumer;
    }
}
