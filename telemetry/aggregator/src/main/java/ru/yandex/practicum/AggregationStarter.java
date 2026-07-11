package ru.yandex.practicum;

import lombok.extern.slf4j.Slf4j;
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
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import ru.yandex.practicum.config.KafkaProperties;
import ru.yandex.practicum.kafka.telemetry.event.*;

@Slf4j
@Component
public class AggregationStarter {

    private final KafkaConsumer<String, SensorEventAvro> consumer;
    private final KafkaProducer<String, SensorsSnapshotAvro> producer;
    private final Map<String, SensorsSnapshotAvro> snapshots = new ConcurrentHashMap<>();
    private final String snapshotTopic;
    private final KafkaProperties kafkaProperties;

    private volatile boolean running = false;
    private Thread workerThread;

    @Autowired
    public AggregationStarter(
            KafkaConsumer<String, SensorEventAvro> consumer,
            KafkaProducer<String, SensorsSnapshotAvro> producer,
            KafkaProperties kafkaProperties) {
        this.consumer = consumer;
        this.producer = producer;
        this.kafkaProperties = kafkaProperties;
        this.snapshotTopic = kafkaProperties.getTopic().getSnapshots();
    }

    public void start() {
        if (running) {
            log.warn("AggregationStarter already started");
            return;
        }
        running = true;
        workerThread = new Thread(this::runLoop, "aggregator-loop");
        workerThread.start();
    }

    public void stop() {
        running = false;
        if (workerThread != null && workerThread.isAlive()) {
            consumer.wakeup();
        }
    }

    private void runLoop() {
        try {
            while (running) {
                ConsumerRecords<String, SensorEventAvro> records = consumer.poll(Duration.ofSeconds(5));
                for (ConsumerRecord<String, SensorEventAvro> record : records) {
                    Optional<SensorsSnapshotAvro> updated = updateState(record.value());
                    if (updated.isPresent()) {
                        SensorsSnapshotAvro currentSnapshot = updated.get();
                        SensorsSnapshotAvro sendSnapshot = createSendSnapshot(currentSnapshot);
                        logSnapshotDetails(sendSnapshot, currentSnapshot.getSensorsState());

                        var pr = new ProducerRecord<>(snapshotTopic, sendSnapshot.getHubId(), sendSnapshot);
                        producer.send(pr, (metadata, exception) -> {
                            if (exception != null) {
                                log.error("Failed to send snapshot for hubId={}", sendSnapshot.getHubId(), exception);
                            } else {
                                log.info("Snapshot sent successfully to topic={}, partition={}, offset={}",
                                        pr.topic(), metadata.partition(), metadata.offset());
                            }
                        });
                    }
                }

                if (!kafkaProperties.getConsumer().isEnableAutoCommit()) {
                    consumer.commitSync();
                }
            }
        } catch (WakeupException ignored) {
            log.info("Aggregation loop woken up — stopping gracefully");
        } catch (Exception e) {
            log.error("Error in aggregation loop", e);
        } finally {
            producer.flush();
            producer.close();
            consumer.close();
        }
    }

    private SensorsSnapshotAvro createSendSnapshot(SensorsSnapshotAvro source) {
        SensorsSnapshotAvro snapshot = new SensorsSnapshotAvro();
        snapshot.setHubId(source.getHubId());
        snapshot.setTimestamp(source.getTimestamp());
        Map<String, SensorStateAvro> states = source.getSensorsState();
        snapshot.setSensorsState(new HashMap<>(states));
        return snapshot;
    }

    private void logSnapshotDetails(SensorsSnapshotAvro snapshot, Map<String, SensorStateAvro> states) {
        log.info("SNAPSHOT FOR SENDING: hubId={}, timestamp={}, sensorsCount={}",
                snapshot.getHubId(),
                snapshot.getTimestamp(),
                states.size());

        for (var entry : states.entrySet()) {
            String deviceId = entry.getKey();
            SensorStateAvro state = entry.getValue();
            Object data = state.getData();
            logSensorDetails(deviceId, data);
        }
    }

    private Optional<SensorsSnapshotAvro> updateState(SensorEventAvro event) {
        String hubId = event.getHubId();
        Instant eventTs = Instant.ofEpochMilli(event.getTimestamp());
        String deviceId = event.getId();

        SensorsSnapshotAvro snapshot = snapshots.computeIfAbsent(hubId, id -> {
            SensorsSnapshotAvro s = new SensorsSnapshotAvro();
            s.setHubId(id);
            s.setTimestamp(eventTs);
            s.setSensorsState(new HashMap<>());
            return s;
        });

        if (eventTs.isAfter(snapshot.getTimestamp())) {
            snapshot.setTimestamp(eventTs);
        }

        Map<String, SensorStateAvro> states = snapshot.getSensorsState();
        SensorStateAvro oldState = states.get(deviceId);

        SensorStateAvro newState = new SensorStateAvro();
        newState.setTimestamp(eventTs);

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
            } else if (eventTs.equals(oldTs) && !dataEquals(oldState.getData(), newState.getData())) {
                stateChanged = true;
            }
        }

        states.put(deviceId, newState);
        return stateChanged ? Optional.of(snapshot) : Optional.empty();
    }

    private boolean dataEquals(Object a, Object b) {
        if (a == b) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        return a.equals(b);
    }

    private void logSensorDetails(String deviceId, Object data) {
        if (data instanceof ClimateSensorAvro climate) {
            log.info("ClimateSensorAvro[{}]: temp_c={}, humidity={}, co2_level={}",
                    deviceId, climate.getTemperatureC(), climate.getHumidity(), climate.getCo2Level());
        } else if (data instanceof LightSensorAvro light) {
            log.info("LightSensorAvro[{}]: link_quality={}, luminosity={}",
                    deviceId, light.getLinkQuality(), light.getLuminosity());
        } else if (data instanceof MotionSensorAvro motion) {
            log.info("MotionSensorAvro[{}]: motion={}, link_quality={}, voltage={}",
                    deviceId, motion.getMotion(), motion.getLinkQuality(), motion.getVoltage());
        } else if (data instanceof SwitchSensorAvro sw) {
            log.info("SwitchSensorAvro[{}]: state={}", deviceId, sw.getState());
        } else if (data instanceof TemperatureSensorAvro temp) {
            log.info("TemperatureSensorAvro[{}]: temp_c={}, temp_f={}",
                    deviceId, temp.getTemperatureC(), temp.getTemperatureF());
        } else {
            log.warn("Unknown sensor type for deviceId={}: {}",
                    deviceId, data != null ? data.getClass().getSimpleName() : "null");
        }
    }
}
