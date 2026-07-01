package ru.yandex.practicum;

import lombok.extern.slf4j.Slf4j;
import org.apache.avro.specific.SpecificRecordBase;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.errors.WakeupException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import ru.yandex.practicum.config.KafkaProperties;
import ru.yandex.practicum.kafka.telemetry.event.*;

@Slf4j
@Component
public class AggregationStarter {

    private KafkaConsumer<String, SensorEventAvro> consumer;
    private KafkaProducer<String, SensorsSnapshotAvro > producer;
    // Храним снапшоты по hubId. Важно: это кэш текущего состояния, НЕ то, что мы отправляем.
    private final Map<String, SensorsSnapshotAvro> snapshots = new ConcurrentHashMap<>();
    private final String snapshotTopic;
    private final KafkaProperties kafkaProperties;

    @Autowired
    public AggregationStarter(
            KafkaConsumer<String, SensorEventAvro> consumer,
            KafkaProducer<String, SensorsSnapshotAvro > producer,
            KafkaProperties kafkaProperties) {
        this.consumer = consumer;
        this.producer = producer;
        this.kafkaProperties = kafkaProperties;
        this.snapshotTopic = kafkaProperties.getTopic().getSnapshots();
    }

    public void start() {
        consumer.subscribe(Collections.singletonList(
                kafkaProperties.getTopic().getSensorEvents() != null
                        ? kafkaProperties.getTopic().getSensorEvents()
                        : "telemetry.sensors.v1"
        ));

        try {
            while (true) {
                ConsumerRecords<String, SensorEventAvro> records = consumer.poll(Duration.ofSeconds(5));
                for (ConsumerRecord<String, SensorEventAvro> record : records) {
                    Optional<SensorsSnapshotAvro> updated = updateState(record.value());
                    if (updated.isPresent()) {
                        SensorsSnapshotAvro currentSnapshot = updated.get();

                        // ВАЖНО: делаем копию снапшота для отправки, чтобы не мутировать кэш
                        SensorsSnapshotAvro sendSnapshot = new SensorsSnapshotAvro();
                        sendSnapshot.setHubId(currentSnapshot.getHubId());
                        sendSnapshot.setTimestamp(currentSnapshot.getTimestamp());

                        Map<String, SensorStateAvro> copiedStates = new HashMap<>(currentSnapshot.getSensorsState());
                        sendSnapshot.setSensorsState(copiedStates);

                        var pr = new ProducerRecord<>(snapshotTopic, sendSnapshot.getHubId(), sendSnapshot);
                        producer.send(pr, (metadata, exception) -> {
                            if (exception != null) {
                                log.error("Failed to send snapshot for hubId={}", sendSnapshot.getHubId(), exception);
                            }
                        });
                    }
                }
                consumer.commitSync();
            }
        } catch (WakeupException ignored) {
            log.info("Graceful shutdown initiated");
        } catch (Exception e) {
            log.error("Error in aggregation loop", e);
        } finally {
            producer.flush();
            consumer.close();
            producer.close();
        }
    }

    private Optional<SensorsSnapshotAvro> updateState(SensorEventAvro event) {
        String hubId = event.getHubId();
        long eventTsLong = event.getTimestamp(); // long из события
        Instant eventTs = Instant.ofEpochMilli(eventTsLong); // конвертируем в Instant для Avro
        String deviceId = event.getId();

        SensorsSnapshotAvro snapshot = snapshots.computeIfAbsent(hubId, id -> {
            SensorsSnapshotAvro s = new SensorsSnapshotAvro();
            s.setHubId(id);
            s.setTimestamp(eventTs); // Instant
            s.setSensorsState(new HashMap<>());
            return s;
        });

        // Обновляем timestamp только если новое событие новее
        if (eventTs.isAfter(snapshot.getTimestamp())) {
            snapshot.setTimestamp(eventTs);
        }

        Map<String, SensorStateAvro> states = snapshot.getSensorsState();
        SensorStateAvro oldState = states.get(deviceId);

        SensorStateAvro newState = new SensorStateAvro();
        newState.setTimestamp(eventTs); // Instant

        Object payloadObj = event.getPayload().getPayload();
        if (payloadObj instanceof ClimateSensorAvro) {
            newState.setData((ClimateSensorAvro) payloadObj);
        } else if (payloadObj instanceof LightSensorAvro) {
            newState.setData((LightSensorAvro) payloadObj);
        } else if (payloadObj instanceof MotionSensorAvro) {
            newState.setData((MotionSensorAvro) payloadObj);
        } else if (payloadObj instanceof SwitchSensorAvro) {
            newState.setData((SwitchSensorAvro) payloadObj);
        } else if (payloadObj instanceof TemperatureSensorAvro) {
            newState.setData((TemperatureSensorAvro) payloadObj);
        } else {
            log.warn("Unknown payload type for deviceId={}, hubId={}", deviceId, hubId);
            return Optional.empty();
        }

        boolean stateChanged = false;

        if (oldState == null) {
            stateChanged = true;
        } else {
            Instant oldTs = oldState.getTimestamp();
            if (eventTs.isAfter(oldTs)) {
                stateChanged = true;
            } else if (eventTs.equals(oldTs)) {
                // Если время одинаковое, проверяем, изменились ли сами данные
                if (!dataEquals(oldState.getData(), newState.getData())) {
                    stateChanged = true;
                }
            }
            // Если eventTs раньше oldTs — игнорируем (старое событие)
        }

        states.put(deviceId, newState);

        return stateChanged ? Optional.of(snapshot) : Optional.empty();
    }

    private boolean dataEquals(Object a, Object b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        return a.equals(b);
    }
}
