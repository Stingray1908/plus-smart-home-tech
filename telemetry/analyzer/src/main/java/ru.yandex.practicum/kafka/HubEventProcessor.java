package ru.yandex.practicum.kafka;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.errors.WakeupException;
import org.springframework.stereotype.Component;
import ru.yandex.practicum.entity.Hub;
import ru.yandex.practicum.entity.Sensor;
import ru.yandex.practicum.kafka.telemetry.event.*;
import ru.yandex.practicum.repository.HubRepository;
import ru.yandex.practicum.repository.SensorRepository;

import java.time.Duration;
import java.util.Collections;

@Getter
@Slf4j
@Component
public class HubEventProcessor implements Runnable {

    private final Consumer<String, HubEventAvro> consumer;
    private final HubRepository hubRepository;
    private final SensorRepository sensorRepository;

    public HubEventProcessor(
            Consumer<String, HubEventAvro> consumer,
            HubRepository hubRepository,
            SensorRepository sensorRepository
    ) {
        this.consumer = consumer;
        this.hubRepository = hubRepository;
        this.sensorRepository = sensorRepository;
    }

    @Override
    public void run() {
        log.info("{} started. Group ID: {}, Topics: {}",
                this.getClass().getSimpleName(),
                // можно вытащить group.id из consumer.metrics() или просто захардкодить для отладки
                "analyzer-hub-events-group", // подставь свой
                "telemetry.hubs.v1");        // подставь свои топики

        try {
            // подписка (если она осталась у тебя в конструкторе — лучше убрать, см. ниже)
            while (true) {
                var records = consumer.poll(java.time.Duration.ofSeconds(5));
                if (records.isEmpty()) {
                    log.trace("No new records, continuing poll...");
                    continue;
                }

                log.info("Polled {} records from topic {}, partitions: {}",
                        records.count(),
                        records.partitions().stream().findFirst().map(tp -> tp.topic()).orElse("unknown"),
                        records.partitions());

                boolean allProcessedSuccessfully = true;

                for (var record : records) {
                    try {
                        log.debug("Processing record: offset={}, partition={}, key={}",
                                record.offset(), record.partition(), record.key());

                        processEvent(record.value()); // или processSnapshot(record.value())

                        log.debug("Record processed: offset={}", record.offset());
                    } catch (Exception e) {
                        allProcessedSuccessfully = false;
                        // ВОТ ЭТО САМОЕ ВАЖНОЕ:
                        log.error("Failed to process record at offset {} partition {}. " +
                                        "Key={}, Value class={}, Stack trace follows:",
                                record.offset(),
                                record.partition(),
                                record.key(),
                                record.value() != null ? record.value().getClass().getName() : "null",
                                e);
                    }
                }

                if (allProcessedSuccessfully) {
                    consumer.commitAsync((offsets, e) -> {
                        if (e != null) {
                            log.error("Async commit failed", e);
                        } else {
                            log.trace("Offsets committed");
                        }
                    });
                } else {
                    log.warn("Batch had errors. Offsets NOT committed. Will retry on next poll.");
                }
            }
        } catch (WakeupException e) {
            log.info("Shutdown signal received, stopping {}", this.getClass().getSimpleName());
        } finally {
            try {
                consumer.close();
                log.info("Consumer closed: {}", this.getClass().getSimpleName());
            } catch (Exception e) {
                log.error("Error closing consumer", e);
            }
        }
    }


    private void processEvent(HubEventAvro event) {
        String hubId = event.getHubId();
        Object payload = event.getPayload();

        // Сначала гарантируем существование хаба
        Hub hub = hubRepository.findById(hubId)
                .orElseGet(() -> {
                    Hub newHub = new Hub();
                    newHub.setId(hubId);
                    newHub.setLocation("Auto-created"); // или null, если location допускает NULL
                    return hubRepository.save(newHub);
                });

        if (payload instanceof DeviceAddedEventAvro devEvent) {
            String sensorId = devEvent.getId();
            // DeviceTypeAvro type = devEvent.getType(); // тип можно сохранить в Sensor, если нужно

            // Используем репозиторий вместо прямого INSERT
            if (!sensorRepository.existsById(sensorId)) {
                Sensor sensor = new Sensor();
                sensor.setId(sensorId);
                sensor.setHub(hub);
                sensorRepository.save(sensor);
                log.debug("Sensor {} added to hub {}", sensorId, hubId);
            } else {
                log.trace("Sensor {} already exists for hub {}", sensorId, hubId);
            }
        }
        else if (payload instanceof DeviceRemovedEventAvro remEvent) {
            String sensorId = remEvent.getId();
            long deleted = sensorRepository.removeByHubIdAndId(hubId, sensorId); // см. ниже доп. метод
            if (deleted > 0) {
                log.info("Sensor {} removed from hub {}", sensorId, hubId);
            }
        }
        else if (payload instanceof ScenarioAddedEventAvro scenEvent) {
            // Для сценариев всё равно придётся использовать JdbcTemplate или отдельный сервис,
            // потому что у тебя нет репозитория для scenario_conditions/scenario_actions.
            // Но хаб мы уже гарантированно создали выше.
            log.debug("ScenarioAddedEventAvro received for hub {}, name={}", hubId, scenEvent.getName());
            // Тут оставь свою существующую логику для сценариев, она не ломает FK.
            // Главное — хаб уже есть.
        }
        else if (payload instanceof ScenarioRemovedEventAvro remScen) {
            String name = remScen.getName();
            log.debug("ScenarioRemovedEventAvro received for hub {}, name={}", hubId, name);
            // Оставь существующую логику удаления сценария
        }
        else {
            log.warn("Unknown payload type in HubEventAvro: {}", payload != null ? payload.getClass().getSimpleName() : "null");
        }
    }
}
