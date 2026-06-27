package ru.yandex.practicum;

import io.grpc.StatusRuntimeException;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import ru.yandex.practicum.entity.Action;
import ru.yandex.practicum.entity.Scenario;
import ru.yandex.practicum.entity.ScenarioAction;
import ru.yandex.practicum.entity.ScenarioCondition;
import ru.yandex.practicum.grpc.telemetry.event.ActionTypeProto;
import ru.yandex.practicum.grpc.telemetry.event.DeviceActionProto;
import ru.yandex.practicum.grpc.telemetry.event.DeviceActionRequest;
import ru.yandex.practicum.grpc.telemetry.hubrouter.HubRouterControllerGrpc;
import ru.yandex.practicum.kafka.telemetry.event.*;
import ru.yandex.practicum.repository.ScenarioActionRepository;
import ru.yandex.practicum.repository.ScenarioConditionRepository;
import ru.yandex.practicum.repository.ScenarioRepository;

import com.google.protobuf.Timestamp; // ПРАВИЛЬНЫЙ импорт для protobuf
import java.time.Instant;
import java.util.List;

@Service
@Slf4j
public class SnapshotAnalyzer {

    private final ScenarioRepository scenarioRepository;
    private final HubRouterControllerGrpc.HubRouterControllerBlockingStub hubRouterClient;
    private ScenarioConditionRepository conditionRepository;
    private ScenarioActionRepository actionRepository;

    @Autowired
    public SnapshotAnalyzer(ScenarioRepository scenarioRepository,
                            @GrpcClient("hub-router") HubRouterControllerGrpc.HubRouterControllerBlockingStub hubRouterClient,
                            ScenarioConditionRepository conditionRepository,
                            ScenarioActionRepository actionRepository) {
        this.scenarioRepository = scenarioRepository;
        this.hubRouterClient = hubRouterClient;
        this.conditionRepository = conditionRepository; // Инициализация
        this.actionRepository = actionRepository;        // Инициализация
    }

    public void processSnapshot(SensorsSnapshotAvro snapshot) {
        String hubId = snapshot.getHubId();
        if (hubId == null) return;

        log.debug("Processing snapshot for hub: {}", hubId);
        List<Scenario> scenarios = scenarioRepository.findByHubId(hubId);

        for (Scenario scenario : scenarios) {
            List<ScenarioCondition> conditions = scenario.getConditions();
            List<ScenarioAction> actions = scenario.getActions();

            if (conditions.isEmpty()) {
                log.debug("Scenario {} has no conditions, skipping.", scenario.getName());
                continue;
            }

            boolean allMet = conditions.stream()
                    .allMatch(cond -> checkCondition(snapshot, cond));

            if (allMet) {
                log.info("Scenario '{}' triggered for hub '{}'. Executing actions.",
                        scenario.getName(), hubId);
                executeActions(snapshot, scenario, actions);
            } else {
                log.trace("Scenario '{}' not triggered.", scenario.getName());
            }
        }
    }

    private List<ScenarioCondition> getConditionsForScenario(Long scenarioId) {
        return conditionRepository.findByScenarioId(scenarioId);
    }

    private List<ScenarioAction> getActionsForScenario(Long scenarioId) {
        return actionRepository.findByScenarioId(scenarioId);
    }

    private boolean checkCondition(SensorsSnapshotAvro snapshot, ScenarioCondition sc) {
        String sensorId = sc.getSensor().getId();
        var state = snapshot.getSensorsState().get(sensorId);

        if (state == null) {
            log.warn("Sensor {} not found in snapshot for hub {}", sensorId, snapshot.getHubId());
            return false;
        }

        Integer currentValue = extractSensorValue(state);
        if (currentValue == null) {
            // Например, это SwitchSensor (boolean) или мы не знаем, как его конвертировать в int
            return false;
        }

        String operation = sc.getCondition().getOperation();
        int threshold = sc.getCondition().getValue();

        return switch (operation) {
            case "<"  -> currentValue < threshold;
            case ">"  -> currentValue > threshold;
            case "==" -> currentValue == threshold;
            default -> {
                log.error("Unknown operation: {}", operation);
                yield false;
            }
        };
    }

    /**
     * Извлекает числовое значение из SensorStateAvro в зависимости от типа датчика.
     * Использует поля из твоей AVRO-схемы: temperature_c, link_quality и т.д.
     */
    private Integer extractSensorValue(SensorStateAvro state) {
        Object data = state.getData();
        if (data == null) return null;

        // TemperatureSensorAvro: поле temperature_c -> getTemperatureC()
        if (data instanceof TemperatureSensorAvro t) {
            return t.getTemperatureC();
        }

        // ClimateSensorAvro: берём температуру (temperature_c) как основное значение для сравнения
        else if (data instanceof ClimateSensorAvro c) {
            return c.getTemperatureC();
        }

        // LightSensorAvro: используем luminosity
        else if (data instanceof LightSensorAvro l) {
            return l.getLuminosity();
        }

        // MotionSensorAvro: у него нет одного "значения".
        // Для примера используем link_quality. Если нужна другая логика — скажи.
        else if (data instanceof MotionSensorAvro m) {
            return m.getLinkQuality();
        }

        // SwitchSensorAvro: это boolean state.
        // Его нельзя напрямую сравнить с int threshold в текущей логике.
        // Возвращаем null, чтобы условие не сработало.
        else if (data instanceof SwitchSensorAvro s) {
            log.debug("Cannot compare SwitchSensor state (boolean) with numeric threshold");
            return null;
        }

        log.debug("Unsupported sensor type for numeric extraction: {}", data.getClass().getSimpleName());
        return null;
    }

    private void executeActions(SensorsSnapshotAvro snapshot, Scenario scenario, List<ScenarioAction> actions) {
        for (ScenarioAction actionLink : actions) {
            Action action = actionLink.getAction();
            String sensorId = actionLink.getSensor().getId();

            String actionTypeString = action.getType();
            // Безопасное получение enum, если вдруг строка не совпадает
            ActionTypeProto protoType;
            try {
                protoType = ActionTypeProto.valueOf(actionTypeString.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                log.warn("Unknown action type '{}', defaulting to ACTIVATE", actionTypeString);
                protoType = ActionTypeProto.ACTIVATE;
            }

            DeviceActionProto deviceAction = DeviceActionProto.newBuilder()
                    .setSensorId(sensorId)
                    .setType(protoType)
                    .setValue(action.getValue() != null ? action.getValue() : 0)
                    .build();

            Instant now = Instant.now();
            Timestamp ts = Timestamp.newBuilder()
                    .setSeconds(now.getEpochSecond())
                    .setNanos(now.getNano())
                    .build();

            DeviceActionRequest request = DeviceActionRequest.newBuilder()
                    .setHubId(snapshot.getHubId())
                    .setScenarioName(scenario.getName())
                    .setAction(deviceAction)
                    .setTimestamp(ts)
                    .build();

            try {
                hubRouterClient.handleDeviceAction(request);
                log.info("gRPC command sent: Hub={}, Sensor={}, Action={}:{}",
                        snapshot.getHubId(), sensorId, action.getType(), action.getValue());
            } catch (StatusRuntimeException e) {
                log.error("Failed to send gRPC command for scenario {}: {}", scenario.getName(), e.getMessage());
            }
        }
    }
}
