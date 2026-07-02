package ru.yandex.practicum.kafka;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.common.errors.WakeupException;
import org.springframework.stereotype.Component;
import ru.yandex.practicum.ScenarioService;
import ru.yandex.practicum.entity.*;
import ru.yandex.practicum.kafka.telemetry.event.*;
import ru.yandex.practicum.repository.*;

import java.time.Duration;
@Getter
@Slf4j
@Component
public class HubEventProcessor implements Runnable {

    private final Consumer<String, HubEventAvro> consumer;
    private final HubRepository hubRepository;
    private final SensorRepository sensorRepository;
    private final ScenarioService scenarioService;

    public HubEventProcessor(
            Consumer<String, HubEventAvro> consumer,
            HubRepository hubRepository,
            SensorRepository sensorRepository,
            ScenarioService scenarioService) {
        this.consumer = consumer;
        this.hubRepository = hubRepository;
        this.sensorRepository = sensorRepository;
        this.scenarioService = scenarioService;
    }

    @Override
    public void run() {
        log.info("{} started.", this.getClass().getSimpleName());
        try {
            while (true) {
                var records = consumer.poll(Duration.ofSeconds(5));
                if (records.isEmpty()) {
                    log.trace("No new records, continuing poll...");
                    continue;
                }

                log.info("Polled {} records from topic", records.count());
                boolean allProcessedSuccessfully = true;

                for (var record : records) {
                    try {
                        log.debug("Processing record: offset={}, partition={}", record.offset(), record.partition());
                        processEvent(record.value());
                        log.debug("Record processed: offset={}", record.offset());
                    } catch (Exception e) {
                        allProcessedSuccessfully = false;
                        log.error("Failed to process record at offset {} partition {}. Value class={}",
                                record.offset(), record.partition(),
                                record.value() != null ? record.value().getClass().getName() : "null", e);
                    }
                }

                if (allProcessedSuccessfully) {
                    consumer.commitAsync((offsets, e) -> {
                        if (e != null) log.error("Async commit failed", e);
                        else log.trace("Offsets committed");
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

        Hub hub = hubRepository.findById(hubId)
                .orElseGet(() -> {
                    Hub newHub = new Hub();
                    newHub.setId(hubId);
                    newHub.setLocation("Auto-created");
                    return hubRepository.save(newHub);
                });

        if (payload instanceof DeviceAddedEventAvro devEvent) {
            String sensorId = devEvent.getId();
            if (!sensorRepository.existsById(sensorId)) {
                Sensor sensor = new Sensor();
                sensor.setId(sensorId);
                sensor.setHub(hub);
                sensorRepository.save(sensor);
                log.debug("Sensor {} added to hub {}", sensorId, hubId);
            }
        } else if (payload instanceof DeviceRemovedEventAvro remEvent) {
            String sensorId = remEvent.getId();
            long deleted = sensorRepository.removeByHubIdAndId(hubId, sensorId);
            if (deleted > 0) {
                log.info("Sensor {} removed from hub {}", sensorId, hubId);
            }
        }
        // ИСПРАВЛЕНО: используем Avro-типы из твоей схемы
        else if (payload instanceof ScenarioAddedEventAvro scenEvent) {
            scenarioService.handleScenarioAdded(hub, scenEvent);
        }
        else if (payload instanceof ScenarioRemovedEventAvro remScen) {
            scenarioService.handleScenarioRemoved(hub, remScen);
        }
        else {
            log.warn("Unknown payload type in HubEventAvro: {}",
                    payload != null ? payload.getClass().getName() : "null");
        }
    }
}
