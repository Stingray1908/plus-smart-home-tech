package ru.yandex.practicum;

import io.grpc.StatusRuntimeException;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Service;

import org.springframework.transaction.annotation.Transactional;
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

@Service
@Slf4j
public class SnapshotAnalyzer {

    private final ScenarioRepository scenarioRepository;
    private final HubRouterControllerGrpc.HubRouterControllerBlockingStub hubRouterClient;

    public SnapshotAnalyzer(ScenarioRepository scenarioRepository,
                            @GrpcClient("hub-router") HubRouterControllerGrpc.HubRouterControllerBlockingStub hubRouterClient) {
        this.scenarioRepository = scenarioRepository;
        this.hubRouterClient = hubRouterClient;
        log.info("SnapshotAnalyzer initialized. gRPC stub created (non-null? {})", hubRouterClient != null);
    }

    @Transactional(readOnly = true)
    public void processSnapshot(SensorsSnapshotAvro snapshot) {
        String hubId = snapshot.getHubId();
        log.debug("Processing snapshot for hub: {}", hubId);

        List<Scenario> scenarios = scenarioRepository.findByHubWithConditions(hubId);
        if (scenarios.isEmpty()) {
            log.trace("No scenarios found for hub={}", hubId);
            return;
        }

        long scenariosWithConditions = scenarios.stream()
                .filter(s -> !s.getConditions().isEmpty())
                .count();

        if (scenariosWithConditions == 0) {
            log.warn("No conditions found for any scenario of hub={}. Nothing to evaluate.", hubId);
            return;
        }

        for (Scenario scenario : scenarios) {
            if (!isScenarioTriggered(scenario, snapshot)) {
                continue;
            }
            log.info("Scenario triggered: name='{}', hub='{}'.", scenario.getName(), hubId);
            executeActions(snapshot, scenario);
        }
    }

    private boolean isScenarioTriggered(Scenario scenario, SensorsSnapshotAvro snapshot) {
        if (scenario == null) {
            return false;
        }
        List<ScenarioCondition> conditions = scenario.getConditions();
        if (conditions == null || conditions.isEmpty()) {
            return false;
        }

        return conditions.stream().allMatch(condition -> {
            Sensor sensor = condition.getSensor();
            if (sensor == null) {
                log.error("Condition in scenario '{}' has no Sensor!", scenario.getName());
                return false;
            }

            Integer currentValue = getSensorValueFromSnapshot(sensor.getId(), snapshot);
            if (currentValue == null) {
                log.trace("Sensor '{}' not found in snapshot. Condition fails.", sensor.getId());
                return false;
            }

            Condition conditionObj = condition.getCondition();
            if (conditionObj == null) {
                log.error("Condition entity is NULL in scenario '{}'!", scenario.getName());
                return false;
            }

            Operation op = parseOperation(conditionObj.getOperation());
            if (op == null) {
                log.warn("Unknown operation '{}' in condition for scenario '{}'. Skipping condition.",
                        conditionObj.getOperation(), scenario.getName());
                return false;
            }

            return evaluateCondition(op, currentValue, conditionObj.getValue());
        });
    }

    private Operation parseOperation(String opStr) {
        if (opStr == null || opStr.isBlank()) {
            return null;
        }
        String s = opStr.trim();
        return switch (s) {
            case "==", "=", "EQUALS" -> Operation.EQ;
            case ">", "GREATER_THAN" -> Operation.GT;
            case "<", "LOWER_THAN" -> Operation.LT;
            default -> {
                log.warn("Unknown operation string: '{}'", opStr);
                yield null;
            }
        };
    }

    private boolean evaluateCondition(Operation op, int current, Object thresholdObj) {
        Integer threshold = switch (thresholdObj) {
            case Integer i -> i;
            case Long l -> l.intValue();
            case String s -> {
                try {
                    yield Integer.parseInt(s);
                } catch (NumberFormatException e) {
                    log.warn("Cannot parse threshold '{}' as int.", s);
                    yield null;
                }
            }
            default -> {
                log.warn("Unexpected threshold type: {}. Expected Integer/Long/String.",
                        thresholdObj.getClass().getSimpleName());
                yield null;
            }
        };

        if (threshold == null) {
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
        if (stateMap == null) {
            return null;
        }
        var state = stateMap.get(sensorId);
        if (state == null) {
            return null;
        }
        return extractSensorValue(state);
    }

    private Integer extractSensorValue(SensorStateAvro state) {
        Object data = state.getData();
        if (data == null) {
            return null;
        }

        if (data instanceof TemperatureSensorAvro t) {
            return t.getTemperatureC();
        }
        if (data instanceof ClimateSensorAvro c) {
            return c.getTemperatureC();
        }
        if (data instanceof LightSensorAvro l) {
            return l.getLuminosity();
        }
        if (data instanceof MotionSensorAvro m) {
            return m.getMotion() ? 1 : 0;
        }
        if (data instanceof SwitchSensorAvro s) {
            return s.getState() ? 1 : 0;
        }

        log.warn("Unsupported sensor type: {}", data.getClass().getSimpleName());
        return null;
    }

    private void executeActions(SensorsSnapshotAvro snapshot, Scenario scenario) {
        List<ScenarioAction> actions = scenario.getActions();
        if (actions == null || actions.isEmpty()) {
            log.debug("Scenario '{}' has no actions to execute.", scenario.getName());
            return;
        }

        Instant now = Instant.now();
        Timestamp ts = Timestamp.newBuilder()
                .setSeconds(now.getEpochSecond())
                .setNanos(now.getNano())
                .build();

        int successCount = 0;
        int failCount = 0;

        for (ScenarioAction action : actions) {
            if (action == null) {
                continue;
            }
            Action actionData = action.getAction();
            Sensor targetSensor = action.getSensor();
            if (actionData == null || targetSensor == null) {
                log.error("Action in scenario '{}' has null Action or Sensor target!", scenario.getName());
                failCount++;
                continue;
            }

            ActionTypeProto protoType = switch (actionData.getType()) {
                case ACTIVATE -> ActionTypeProto.ACTIVATE;
                case DEACTIVATE -> ActionTypeProto.DEACTIVATE;
                case SET_TEMP -> ActionTypeProto.SET_VALUE;
                default -> throw new IllegalStateException("Unsupported action type: " + actionData.getType());
            };

            DeviceActionProto deviceAction = DeviceActionProto.newBuilder()
                    .setSensorId(targetSensor.getId())
                    .setType(protoType)
                    .setValue(actionData.getValue() != null ? actionData.getValue() : 0)
                    .build();

            var request = DeviceActionRequest.newBuilder()
                    .setHubId(snapshot.getHubId())
                    .setScenarioName(scenario.getName())
                    .setAction(deviceAction)
                    .setTimestamp(ts)
                    .build();

            try {
                hubRouterClient.handleDeviceAction(request);
                successCount++;
                log.debug("[GRPC OK] Sent to Hub Router: hub={}, sensor={}, action={}",
                        request.getHubId(), request.getAction().getSensorId(), request.getAction().getType());
            } catch (StatusRuntimeException e) {
                failCount++;
                log.error("[GRPC FAIL] Error for scenario '{}': code={}, desc={}",
                        scenario.getName(), e.getStatus().getCode(), e.getStatus().getDescription(), e);
            } catch (Exception e) {
                failCount++;
                log.error("[UNEXPECTED] Unexpected error sending action for scenario '{}'",
                        scenario.getName(), e);
            }
        }

        if (failCount > 0) {
            log.warn("Scenario '{}': executed {} actions, {} failed.",
                    scenario.getName(), successCount, failCount);
        } else {
            log.info("Scenario '{}': all {} actions executed successfully.",
                    scenario.getName(), successCount);
        }
    }
}
