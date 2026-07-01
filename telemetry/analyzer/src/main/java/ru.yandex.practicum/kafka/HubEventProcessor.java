package ru.yandex.practicum.kafka;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
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

    private final KafkaConsumer<String, HubEventAvro> consumer;
    private final HubRepository hubRepository;
    private final SensorRepository sensorRepository;

    public HubEventProcessor(
            KafkaConsumer<String, HubEventAvro> consumer,
            HubRepository hubRepository,
            SensorRepository sensorRepository
    ) {
        this.consumer = consumer;
        this.hubRepository = hubRepository;
        this.sensorRepository = sensorRepository;
        this.consumer.subscribe(Collections.singletonList("telemetry.hubs.v1"));
    }

    @Override
    public void run() {
        log.info("HubEventProcessor started");
        try {
            while (true) {
                var records = consumer.poll(Duration.ofSeconds(5));
                if (records.isEmpty()) continue;

                boolean allProcessedSuccessfully = true;
                for (var record : records) {
                    HubEventAvro event = record.value();
                    if (event == null) continue;

                    try {
                        processEvent(event);
                        log.debug("Processed hub event, hubId={}", event.getHubId());
                    } catch (Exception e) {
                        allProcessedSuccessfully = false;
                        log.error("Failed to process hub event at offset {} partition {}. Will retry on next poll.",
                                record.offset(), record.partition(), e);
                    }
                }

                if (allProcessedSuccessfully) {
                    consumer.commitAsync((offsets, e) -> {
                        if (e != null) {
                            log.error("Async commit failed", e);
                        } else {
                            log.trace("Offsets committed asynchronously");
                        }
                    });
                } else {
                    log.warn("Batch contained errors. Offsets NOT committed. Retrying on next iteration.");
                }
            }
        } catch (WakeupException e) {
            log.info("HubEventProcessor received shutdown signal");
        } finally {
            consumer.close();
            log.info("HubEventProcessor closed");
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
