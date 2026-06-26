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
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import ru.yandex.practicum.config.KafkaProperties;
import ru.yandex.practicum.kafka.telemetry.event.*;

@Slf4j
@Component
public class AggregationStarter {
    private KafkaConsumer<String, SensorEventAvro> consumer;
    private KafkaProducer<String, SpecificRecordBase> producer;
    private final Map<String, SensorsSnapshotAvro> snapshots = new ConcurrentHashMap<>();
    private String snapshotTopic;

    @Autowired
    public AggregationStarter(
            KafkaConsumer<String, SensorEventAvro> consumer,
            KafkaProducer<String, SpecificRecordBase> producer,
            KafkaProperties kafkaProperties) {
        this.consumer = consumer;
        this.producer = producer;
        this.snapshotTopic = kafkaProperties.getTopic().getSnapshots();
    }

    public void start() {
        try {
            while (true) {
                ConsumerRecords<String, SensorEventAvro> records = consumer.poll(Duration.ofSeconds(5));

                for (ConsumerRecord<String, SensorEventAvro> record : records) {
                    Optional<SensorsSnapshotAvro> updated = updateState(record.value());
                    if (updated.isPresent()) {
                        SensorsSnapshotAvro snapshot = updated.get();
                        // Приводим к SpecificRecordBase при отправке — это нормально
                        var pr = new ProducerRecord<>(snapshotTopic, snapshot.getHubId(), (SpecificRecordBase) snapshot);
                        producer.send(pr, (metadata, exception) -> {
                            if (exception != null) {
                                log.error("Failed to send snapshot for hubId={}", snapshot.getHubId(), exception);
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
        SensorsSnapshotAvro snapshot = snapshots.computeIfAbsent(hubId, id -> {
            SensorsSnapshotAvro s = new SensorsSnapshotAvro();
            s.setHubId(id);
            // Конвертируем Instant -> long (millis)
            Instant ts = Instant.now();
            s.setTimestamp(ts);
            s.setSensorsState(new ConcurrentHashMap<>());
            return s;
        });

        // Конвертируем event.timestamp (long) -> Instant для сравнения
        Instant eventInstant = Instant.ofEpochMilli(event.getTimestamp());
        Instant snapInstant = snapshot.getTimestamp();

        if (eventInstant.isAfter(snapInstant)) {
            snapshot.setTimestamp(Instant.ofEpochMilli(event.getTimestamp())); // пишем обратно long
        }

        Map<String, SensorStateAvro> states = snapshot.getSensorsState();
        String deviceId = event.getId();

        SensorStateAvro oldState = states.get(deviceId);
        if (oldState != null) {
            Instant oldInstant = oldState.getTimestamp();
            if (!eventInstant.isAfter(oldInstant)) {
                return Optional.empty();
            }
        }

        SensorStateAvro newState = new SensorStateAvro();
        newState.setTimestamp(Instant.ofEpochMilli(event.getTimestamp())); // long

        Object payload = event.getPayload().getPayload();
        if (payload instanceof ClimateSensorAvro) {
            newState.setData((ClimateSensorAvro) payload);
        } else if (payload instanceof LightSensorAvro) {
            newState.setData((LightSensorAvro) payload);
        } else if (payload instanceof MotionSensorAvro) {
            newState.setData((MotionSensorAvro) payload);
        } else if (payload instanceof SwitchSensorAvro) {
            newState.setData((SwitchSensorAvro) payload);
        } else if (payload instanceof TemperatureSensorAvro) {
            newState.setData((TemperatureSensorAvro) payload);
        } else {
            log.warn("Unknown payload type for deviceId={}, hubId={}", deviceId, hubId);
            return Optional.empty();
        }

        states.put(deviceId, newState);
        return Optional.of(snapshot);
    }
}
