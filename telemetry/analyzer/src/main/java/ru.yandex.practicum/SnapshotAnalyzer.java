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
        log.info("🟢 SnapshotAnalyzer initialized. gRPC stub created (non-null? {})", hubRouterClient != null);
    }

    public void processSnapshot(SensorsSnapshotAvro snapshot) {
        String hubId = snapshot.getHubId();

        if (hubId == null) {
            log.error("🔴 INVALID SNAPSHOT: hubId is NULL! Skipping.");
            return;
        }
        var sensorsState = snapshot.getSensorsState();
        if (sensorsState == null || sensorsState.isEmpty()) {
            log.warn("🟡 INVALID SNAPSHOT: sensorsState is null or empty for hub={}. Skipping.", hubId);
            return;
        }

        log.info("🎯 START Processing snapshot for hub: {}", hubId);

        List<Scenario> scenarios = scenarioRepository.findByHubId(hubId);
        if (scenarios == null || scenarios.isEmpty()) {
            log.error("🔴 NO SCENARIOS: No scenarios found for hub={}. Check DB.", hubId);
            return;
        }

        log.info("✅ FOUND {} scenarios for hub={}", scenarios.size(), hubId);

        long scenariosWithConditions = scenarios.stream()
                .filter(s -> s.getConditions() != null && !s.getConditions().isEmpty())
                .count();
        log.info("📋 {} out of {} scenarios have non-empty conditions.", scenariosWithConditions, scenarios.size());

        if (scenariosWithConditions == 0) {
            log.error("🔴 EMPTY CONDITIONS: All scenarios for hub={} have no conditions.", hubId);
            return;
        }

        scenarios.stream()
                .filter(scenario -> {
                    boolean triggered = isScenarioTriggered(scenario, snapshot);
                    if (triggered) {
                        log.info(">>> 🟢 SCENARIO TRIGGERED: '{}' for hub '{}'.", scenario.getName(), hubId);
                    } else {
                        log.debug("🟡 SCENARIO NOT TRIGGERED: '{}' for hub '{}'.", scenario.getName(), hubId);
                    }
                    return triggered;
                })
                .forEach(scenario -> executeActions(snapshot, scenario));
    }

    private boolean isScenarioTriggered(Scenario scenario, SensorsSnapshotAvro snapshot) {
        if (scenario == null) return false;

        List<ScenarioCondition> conditions = scenario.getConditions();
        if (conditions == null || conditions.isEmpty()) {
            return false;
        }

        return conditions.stream()
                .allMatch(condition -> {
                    Sensor sensor = condition.getSensor();
                    if (sensor == null) {
                        log.error("❌ CRITICAL: Condition in scenario '{}' has NO Sensor!", scenario.getName());
                        return false;
                    }

                    Integer currentValue = getSensorValueFromSnapshot(sensor.getId(), snapshot);
                    if (currentValue == null) {
                        log.debug("⚠️ Sensor '{}' not found in snapshot. Condition fails.", sensor.getId());
                        return false;
                    }

                    Condition conditionObj = condition.getCondition();
                    if (conditionObj == null) {
                        log.error("❌ CRITICAL: Condition entity is NULL in scenario '{}'!", scenario.getName());
                        return false;
                    }

// Маппим строковую операцию из БД в наш enum Operation
                    Operation op = parseOperation(conditionObj.getOperation());
                    if (op == null) {
                        log.warn("⚠️ Unknown operation '{}' in condition for scenario '{}'. Skipping condition.",
                                conditionObj.getOperation(), scenario.getName());
                        return false;
                    }

                    Object thresholdObj = conditionObj.getValue();

                    log.trace("🧮 Check: sensor={}, op={}, current={}, threshold={}",
                            sensor.getId(), op, currentValue, thresholdObj);

                    return evaluateCondition(op, currentValue, thresholdObj);
                });
    }

    private Operation parseOperation(String opStr) {
        if (opStr == null || opStr.isBlank()) return null;

        switch (opStr.trim().toLowerCase()) {
            case "<":
            case "lt":
                return Operation.LT;
            case ">":
            case "gt":
                return Operation.GT;
            case "==":
            case "=":
            case "eq":
                return Operation.EQ;
            default:
                return null;
        }
    }

    private boolean evaluateCondition(Operation op, int current, Object thresholdObj) {
        Integer threshold;
        if (thresholdObj instanceof Integer i) {
            threshold = i;
        } else if (thresholdObj instanceof Long l) {
            threshold = l.intValue();
        } else if (thresholdObj instanceof String s) {
            try {
                threshold = Integer.parseInt(s);
            } catch (NumberFormatException e) {
                log.warn("⚠️ Cannot parse threshold '{}' as int.", s);
                return false;
            }
        } else {
            log.warn("⚠️ Unexpected threshold type: {}. Expected Integer/Long/String. Got: {}",
                    thresholdObj.getClass().getSimpleName(), thresholdObj);
            return false;
        }

        return switch (op) {
            case GT -> current > threshold;
            case LT -> current < threshold;
            case EQ -> current == threshold;
            default -> false;
        };
    }

    private Integer getSensorValueFromSnapshot(String sensorId, SensorsSnapshotAvro snapshot) {
        var stateMap = snapshot.getSensorsState();
        if (stateMap == null) return null;
        var state = stateMap.get(sensorId);
        if (state == null) return null;
        return extractSensorValue(state);
    }

    private Integer extractSensorValue(SensorStateAvro state) {
        Object data = state.getData();
        if (data == null) return null;

        if (data instanceof TemperatureSensorAvro t) {
            return t.getTemperatureC();
        } else if (data instanceof ClimateSensorAvro c) {
            return c.getTemperatureC();
        } else if (data instanceof LightSensorAvro l) {
            return l.getLuminosity();
        } else if (data instanceof MotionSensorAvro m) {
            return m.getMotion() ? 1 : 0;
        } else if (data instanceof SwitchSensorAvro s) {
            return s.getState() ? 1 : 0;
        }

        log.warn("⚠️ Unsupported sensor type: {}", data.getClass().getSimpleName());
        return null;
    }

    private void executeActions(SensorsSnapshotAvro snapshot, Scenario scenario) {
        List<ScenarioAction> actions = scenario.getActions();
        if (actions == null || actions.isEmpty()) {
            log.warn("⚠️ Scenario '{}' has no actions to execute.", scenario.getName());
            return;
        }

        log.info(">>> EXECUTING {} actions for scenario '{}'", actions.size(), scenario.getName());

        actions.stream()
                .map(action -> {
                    if (action == null) return null;

                    Action actionData = action.getAction();
                    Sensor targetSensor = action.getSensor();

                    if (actionData == null) {
                        log.error("❌ Action in scenario '{}' has null Action!", scenario.getName());
                        return null;
                    }
                    if (targetSensor == null) {
                        log.error("❌ Action in scenario '{}' has null Sensor target!", scenario.getName());
                        return null;
                    }

                    String typeStr = actionData.getType();
                    if (typeStr == null || typeStr.trim().isEmpty()) {
                        log.error("❌ Action in scenario '{}' has empty type!", scenario.getName());
                        return null;
                    }

                    try {
                        ActionTypeProto protoType = ActionTypeProto.valueOf(typeStr.trim().toUpperCase());

                        DeviceActionProto deviceAction = DeviceActionProto.newBuilder()
                                .setSensorId(targetSensor.getId())
                                .setType(protoType)
                                .setValue(actionData.getValue() != null ? actionData.getValue() : 0)
                                .build();

                        Instant now = Instant.now();
                        Timestamp ts = Timestamp.newBuilder()
                                .setSeconds(now.getEpochSecond())
                                .setNanos(now.getNano())
                                .build();

                        var request = DeviceActionRequest.newBuilder()
                                .setHubId(snapshot.getHubId())
                                .setScenarioName(scenario.getName())
                                .setAction(deviceAction)
                                .setTimestamp(ts)
                                .build();

                        return request;
                    } catch (IllegalArgumentException e) {
                        log.error("❌ Invalid action type '{}' in scenario '{}'.", typeStr, scenario.getName(), e);
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .forEach(request -> {
                    log.info("[GRPC CALL] Sending to Hub Router: hub={}, sensor={}, action={}",
                            request.getHubId(), request.getAction().getSensorId(), request.getAction().getType());
                    try {
                        hubRouterClient.handleDeviceAction(request);
                        log.info("🟢 [OK] Sent to Hub Router successfully.");
                    } catch (StatusRuntimeException e) {
                        log.error("🔴 [GRPC FAIL] Error for scenario '{}': code={}, desc={}",
                                scenario.getName(), e.getStatus().getCode(), e.getStatus().getDescription(), e);
                    } catch (Exception e) {
                        log.error("🔴 [UNEXPECTED] Unexpected error sending action for scenario '{}'",
                                scenario.getName(), e);
                    }
                });
    }
}
