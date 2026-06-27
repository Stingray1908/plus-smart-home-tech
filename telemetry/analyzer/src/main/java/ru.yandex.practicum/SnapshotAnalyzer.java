package ru.yandex.practicum;

import io.grpc.StatusRuntimeException;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Service;

import ru.yandex.practicum.entity.*;
import ru.yandex.practicum.enums.Operation;
import ru.yandex.practicum.grpc.telemetry.event.ActionTypeProto;
import ru.yandex.practicum.grpc.telemetry.event.DeviceActionProto;
import ru.yandex.practicum.grpc.telemetry.event.DeviceActionRequest;
import ru.yandex.practicum.grpc.telemetry.hubrouter.HubRouterControllerGrpc;
import ru.yandex.practicum.kafka.telemetry.event.*;
import ru.yandex.practicum.repository.ScenarioRepository;

import com.google.protobuf.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

@Service
@Slf4j
public class SnapshotAnalyzer {

    private final ScenarioRepository scenarioRepository;
    private final HubRouterControllerGrpc.HubRouterControllerBlockingStub hubRouterClient;

    public SnapshotAnalyzer(ScenarioRepository scenarioRepository,
                            @GrpcClient("hub-router") HubRouterControllerGrpc.HubRouterControllerBlockingStub hubRouterClient) {
        this.scenarioRepository = scenarioRepository;
        this.hubRouterClient = hubRouterClient;
    }

    public void processSnapshot(SensorsSnapshotAvro snapshot) {
        String hubId = snapshot.getHubId();
        if (hubId == null || snapshot.getSensorsState() == null) {
            log.warn("Invalid snapshot: hubId={}, sensorsState={}", hubId, snapshot.getSensorsState());
            return;
        }

        log.debug("Processing snapshot for hub: {}", hubId);
        List<Scenario> scenarios = scenarioRepository.findByHubId(hubId);

        if (scenarios.isEmpty()) {
            return;
        }

        // Передаем snapshot внутрь проверки
        scenarios.stream()
                .filter(scenario -> isScenarioTriggered(scenario, snapshot))
                .forEach(scenario -> {
                    log.info("Scenario '{}' triggered for hub '{}'. Executing actions.",
                            scenario.getName(), hubId);
                    executeActions(snapshot, scenario);
                });
    }

    // Теперь метод принимает snapshot явно
    private boolean isScenarioTriggered(Scenario scenario, SensorsSnapshotAvro snapshot) {
        if (scenario.getConditions().isEmpty()) return false;

        return scenario.getConditions().stream()
                .allMatch(conditionLink -> {
                    Sensor sensor = conditionLink.getSensor();
                    Condition condition = conditionLink.getCondition();

                    if (sensor == null || condition == null) return false;

                    // ВОТ ЗДЕСЬ БЫЛА ОШИБКА: теперь snapshot передается
                    Integer currentValue = getSensorValueFromSnapshot(sensor.getId(), snapshot);

                    if (currentValue == null) {
                        log.warn("Sensor {} not found in snapshot.", sensor.getId());
                        return false;
                    }

                    return evaluateCondition(Operation.valueOf(condition.getOperation()), currentValue, condition.getValue());
                });
    }

    // Вынес логику сравнения в отдельный метод — так чище
    private boolean evaluateCondition(Operation op, int current, int threshold) {
        return switch (op) {
            case GT -> current > threshold;
            case LT -> current < threshold;
            case EQ -> current == threshold;
        };
    }

    private Integer getSensorValueFromSnapshot(String sensorId, SensorsSnapshotAvro snapshot) {
        var state = snapshot.getSensorsState().get(sensorId);
        if (state == null) return null;
        return extractSensorValue(state);
    }

    private Integer extractSensorValue(SensorStateAvro state) {
        Object data = state.getData();
        if (data == null) return null;

        if (data instanceof TemperatureSensorAvro t) return t.getTemperatureC();
        else if (data instanceof ClimateSensorAvro c) return c.getTemperatureC();
        else if (data instanceof LightSensorAvro l) return l.getLuminosity();
        else if (data instanceof MotionSensorAvro m) return m.getMotion() ? 1 : 0;
        else if (data instanceof SwitchSensorAvro s) return s.getState() ? 1 : 0;

        log.debug("Unsupported sensor type: {}", data.getClass().getSimpleName());
        return null;
    }

    private void executeActions(SensorsSnapshotAvro snapshot, Scenario scenario) {
        scenario.getActions().stream()
                .map(actionLink -> {
                    Action action = actionLink.getAction();
                    Sensor sensor = actionLink.getSensor();
                    if (action == null || sensor == null) return null;

                    try {
                        ActionTypeProto protoType = ActionTypeProto.valueOf(action.getType().trim().toUpperCase());

                        DeviceActionProto deviceAction = DeviceActionProto.newBuilder()
                                .setSensorId(sensor.getId())
                                .setType(protoType)
                                .setValue(action.getValue() != null ? action.getValue() : 0)
                                .build();

                        Instant now = Instant.now();
                        Timestamp ts = Timestamp.newBuilder()
                                .setSeconds(now.getEpochSecond())
                                .setNanos(now.getNano())
                                .build();

                        return DeviceActionRequest.newBuilder()
                                .setHubId(snapshot.getHubId())
                                .setScenarioName(scenario.getName())
                                .setAction(deviceAction)
                                .setTimestamp(ts)
                                .build();
                    } catch (IllegalArgumentException e) {
                        log.error("Invalid action type '{}' in scenario '{}'", action.getType(), scenario.getName(), e);
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .forEach(request -> {
                    try {
                        hubRouterClient.handleDeviceAction(request);
                        log.info("[OK] Sent: Hub={}, Sensor={}, Action={}",
                                request.getHubId(), request.getAction().getSensorId(), request.getAction().getType());
                    } catch (StatusRuntimeException e) {
                        log.error("[FAIL] gRPC error for scenario '{}': {} {}",
                                scenario.getName(), e.getStatus().getCode(), e.getStatus().getDescription(), e);
                    }
                });
    }
}
