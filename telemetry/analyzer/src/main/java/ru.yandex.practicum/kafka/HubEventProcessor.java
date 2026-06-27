package ru.yandex.practicum.kafka;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import ru.yandex.practicum.kafka.telemetry.event.*;

import java.time.Duration;
import java.util.Collections;

@Getter
@Slf4j
@Component
public class HubEventProcessor implements Runnable {

    private final KafkaConsumer<String, HubEventAvro> consumer;
    private final JdbcTemplate jdbcTemplate;

    public HubEventProcessor(KafkaConsumer<String, HubEventAvro> consumer, JdbcTemplate jdbcTemplate) {
        this.consumer = consumer;
        this.jdbcTemplate = jdbcTemplate;
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
                            // Здесь можно добавить повторную попытку, если критично
                        } else {
                            log.trace("Offsets committed asynchronously");
                        }
                    });
                } else {
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

    private void processEvent(HubEventAvro event) {
        String hubId = event.getHubId(); // Это есть прямо в HubEventAvro
        Object payload = event.getPayload(); // Получаем содержимое union

        if (payload instanceof DeviceAddedEventAvro devEvent) {
            // Обработка: добавили устройство (датчик)
            String sensorId = devEvent.getId();
            DeviceTypeAvro type = devEvent.getType();

            String sql = "INSERT INTO sensors (id, hub_id) VALUES (?, ?) ON CONFLICT (id) DO NOTHING";
            jdbcTemplate.update(sql, sensorId, hubId);
            log.debug("Sensor {} (type: {}) added to hub {}", sensorId, type, hubId);
        }
        else if (payload instanceof DeviceRemovedEventAvro remEvent) {
            // Обработка: удалили устройство
            String sensorId = remEvent.getId();
            String sql = "DELETE FROM sensors WHERE id = ? AND hub_id = ?";
            int rows = jdbcTemplate.update(sql, sensorId, hubId);
            if (rows > 0) log.info("Sensor {} removed from hub {}", sensorId, hubId);
        }
        else if (payload instanceof ScenarioAddedEventAvro scenEvent) {
            // Обработка: добавили сценарий (самое сложное)
            String scenarioName = scenEvent.getName();

            // 1. Сначала сохраняем сам сценарий
            String insertScenarioSql = "INSERT INTO scenarios (hub_id, name) VALUES (?, ?) ON CONFLICT (hub_id, name) DO NOTHING RETURNING id";
            // Используем queryForObject, чтобы получить ID сценария, если он был создан
            Long scenarioId = null;
            try {
                scenarioId = jdbcTemplate.queryForObject(insertScenarioSql, Long.class, hubId, scenarioName);
            } catch (Exception e) {
                // Если запись уже была, queryForObject может кинуть исключение,
                // но нам важно просто получить ID существующего сценария.
                // Для простоты можно сначала сделать SELECT, но давай сделаем проще:
            }

            // Если сценарий уже был, надо найти его ID
            if (scenarioId == null) {
                String selectIdSql = "SELECT id FROM scenarios WHERE hub_id = ? AND name = ?";
                scenarioId = jdbcTemplate.queryForObject(selectIdSql, Long.class, hubId, scenarioName);
            }

            log.debug("Scenario '{}' ensured for hub {}, ID={}", scenarioName, hubId, scenarioId);

            // 2. Сохраняем условия (conditions) из массива
            for (ScenarioConditionAvro cond : scenEvent.getConditions()) {
                String sensorId = cond.getSensorId();
                ConditionTypeAvro cType = cond.getType();
                ConditionOperationAvro op = cond.getOperation();
                Integer value = cond.getValue() != null ? (int)cond.getValue() : null; // обработка union {null, int}

                String condSql = """
                INSERT INTO conditions (type, operation, value) 
                VALUES (?, ?, ?) 
                ON CONFLICT DO NOTHING
                """;
                jdbcTemplate.update(condSql, cType.name(), op.name(), value);

                // Теперь нужно связать условие со сценарием и датчиком.
                // В ТЗ есть таблица scenario_conditions.
                // Но тут нюанс: в Avro у условия нет ID, оно идентифицируется по типу/операции/значению.
                // Для учебного проекта можно считать, что комбинация (type, op, value) уникальна.
                // Тогда делаем SELECT, чтобы найти ID условия, и вставляем в связь.
                String getCondIdSql = "SELECT id FROM conditions WHERE type = ? AND operation = ? AND value = ?";
                Long conditionId = jdbcTemplate.queryForObject(getCondIdSql, Long.class, cType.name(), op.name(), value);

                if (conditionId != null && scenarioId != null) {
                    String linkCondSql = """
                    INSERT INTO scenario_conditions (scenario_id, sensor_id, condition_id) 
                    VALUES (?, ?, ?) 
                    ON CONFLICT DO NOTHING
                    """;
                    jdbcTemplate.update(linkCondSql, scenarioId, sensorId, conditionId);
                }
            }

            // 3. Сохраняем действия (actions) по аналогии
            for (DeviceActionAvro action : scenEvent.getActions()) {
                String sensorId = action.getSensorId();
                ActionTypeAvro aType = action.getType();
                Integer val = action.getValue() != null ? action.getValue().intValue() : null;

                String actSql = "INSERT INTO actions (type, value) VALUES (?, ?) ON CONFLICT DO NOTHING";
                jdbcTemplate.update(actSql, aType.name(), val);

                String getActIdSql = "SELECT id FROM actions WHERE type = ? AND value = ?";
                Long actionId = jdbcTemplate.queryForObject(getActIdSql, Long.class, aType.name(), val);

                if (actionId != null && scenarioId != null) {
                    String linkActSql = """
                    INSERT INTO scenario_actions (scenario_id, sensor_id, action_id) 
                    VALUES (?, ?, ?) 
                    ON CONFLICT DO NOTHING
                    """;
                    jdbcTemplate.update(linkActSql, scenarioId, sensorId, actionId);
                }
            }
        }
        else if (payload instanceof ScenarioRemovedEventAvro remScen) {
            String name = remScen.getName();
            String deleteSql = "DELETE FROM scenarios WHERE hub_id = ? AND name = ?";
            int rows = jdbcTemplate.update(deleteSql, hubId, name);
            if (rows > 0) log.info("Scenario '{}' removed from hub {}", name, hubId);
        }
        else {
            log.warn("Unknown payload type in HubEventAvro: {}", payload != null ? payload.getClass().getSimpleName() : "null");
        }
    }
}
