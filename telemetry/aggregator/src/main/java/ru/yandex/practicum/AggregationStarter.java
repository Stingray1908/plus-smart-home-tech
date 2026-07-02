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

    private final KafkaConsumer<String, SensorEventAvro> consumer;
    private final KafkaProducer<String, SensorsSnapshotAvro> producer;
    // Храним снапшоты по hubId. Важно: это кэш текущего состояния, НЕ то, что мы отправляем.
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

    /**
     * Запускает агрегацию в отдельном потоке.
     */
    public void start() {
        if (running) {
            log.warn("AggregationStarter already started");
            return;
        }
        running = true;
        workerThread = new Thread(this::runLoop, "aggregator-loop");
        workerThread.start();
    }

    /**
     * Корректно останавливает агрегацию: снимает флаг, будит консьюмер, чтобы он вышел из poll().
     */
    public void stop() {
        running = false;
        if (workerThread != null && workerThread.isAlive()) {
            consumer.wakeup(); // вытаскивает consumer из poll() с WakeupException
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

                        // Создаём снапшот для отправки
                        SensorsSnapshotAvro sendSnapshot = new SensorsSnapshotAvro();

                        // 1. Копируем основные идентификаторы (это всё, что есть в схеме SensorsSnapshotAvro)
                        sendSnapshot.setHubId(currentSnapshot.getHubId());
                        sendSnapshot.setTimestamp(currentSnapshot.getTimestamp());

                        // 2. Копируем карту состояний (sensorsState) — это главный источник правды для тестов
                        Map<String, SensorStateAvro> states = currentSnapshot.getSensorsState();
                        sendSnapshot.setSensorsState(new HashMap<>(states));

                        // --- ОТЛАДКА: выводим, что реально улетит в Kafka ---
                        log.info("📦 SNAPSHOT FOR SENDING: hubId={}, timestamp={}, sensorsCount={}",
                                sendSnapshot.getHubId(),
                                sendSnapshot.getTimestamp(),
                                states.size());

                        for (var entry : states.entrySet()) {
                            String deviceId = entry.getKey();
                            SensorStateAvro state = entry.getValue();
                            Object data = state.getData();

                            if (data instanceof ClimateSensorAvro climate) {
                                log.info("  🌡️ ClimateSensorAvro[{}]: temp_c={}, humidity={}, co2_level={}",
                                        deviceId, climate.getTemperatureC(), climate.getHumidity(), climate.getCo2Level());
                            } else if (data instanceof LightSensorAvro light) {
                                log.info("  💡 LightSensorAvro[{}]: link_quality={}, luminosity={}",
                                        deviceId, light.getLinkQuality(), light.getLuminosity());
                            } else if (data instanceof MotionSensorAvro motion) {
                                log.info("  🏃 MotionSensorAvro[{}]: motion={}, link_quality={}, voltage={}",
                                        deviceId, motion.getMotion(), motion.getLinkQuality(), motion.getVoltage());
                            } else if (data instanceof SwitchSensorAvro sw) {
                                log.info("  🔌 SwitchSensorAvro[{}]: state={}", deviceId, sw.getState());
                            } else if (data instanceof TemperatureSensorAvro temp) {
                                log.info("  🌡️ TemperatureSensorAvro[{}]: temp_c={}, temp_f={}",
                                        deviceId, temp.getTemperatureC(), temp.getTemperatureF());
                            } else {
                                log.warn("  ❓ Unknown sensor type for deviceId={}: {}", deviceId, data != null ? data.getClass().getSimpleName() : "null");
                            }
                        }
                        // -----------------------------------------------------

                        var pr = new ProducerRecord<>(snapshotTopic, sendSnapshot.getHubId(), sendSnapshot);
                        producer.send(pr, (metadata, exception) -> {
                            if (exception != null) {
                                log.error("Failed to send snapshot for hubId={}", sendSnapshot.getHubId(), exception);
                            } else {
                                log.info("✅ Snapshot sent successfully to topic={}, partition={}, offset={}",
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
