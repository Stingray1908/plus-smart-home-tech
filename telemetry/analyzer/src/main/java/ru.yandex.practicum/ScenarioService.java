package ru.yandex.practicum;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.yandex.practicum.entity.*;
import ru.yandex.practicum.kafka.telemetry.event.*; // твои Avro-классы
import ru.yandex.practicum.repository.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@Slf4j
public class ScenarioService {

    private final ScenarioRepository scenarioRepository;
    private final ConditionRepository conditionRepository;
    private final ActionRepository actionRepository;
    private final SensorRepository sensorRepository;
    private final ScenarioConditionRepository scenarioConditionRepository;
    private final ScenarioActionRepository scenarioActionRepository;

    public ScenarioService(ScenarioRepository scenarioRepository,
                           ConditionRepository conditionRepository,
                           ActionRepository actionRepository,
                           SensorRepository sensorRepository,
                           ScenarioConditionRepository scenarioConditionRepository,
                           ScenarioActionRepository scenarioActionRepository) {
        this.scenarioRepository = scenarioRepository;
        this.conditionRepository = conditionRepository;
        this.actionRepository = actionRepository;
        this.sensorRepository = sensorRepository;
        this.scenarioConditionRepository = scenarioConditionRepository;
        this.scenarioActionRepository = scenarioActionRepository;
    }

    @Transactional
    public void handleScenarioAdded(Hub hub, ScenarioAddedEventAvro event) {
        String scenarioName = event.getName();
        log.info("Processing SCENARIO_ADDED: hub={}, name={}", hub.getId(), scenarioName);

        Optional<Scenario> existing = scenarioRepository.findByHubIdAndName(hub.getId(), scenarioName);
        Scenario scenario = existing.orElseGet(() -> {
            Scenario s = new Scenario();
            s.setHub(hub);
            s.setName(scenarioName);
            return s;
        });

        scenario = scenarioRepository.save(scenario);
        Long scenarioId = scenario.getId();

        scenarioConditionRepository.deleteByScenarioId(scenarioId);
        scenario.getConditions().clear();

        List<ScenarioCondition> conditionLinks = new ArrayList<>();
        for (ScenarioConditionAvro avroCond : event.getConditions()) {
            Integer value = extractValueFromAvroUnion(avroCond.getValue());
            if (value == null) {
                log.warn("Condition for scenario {} has no value, skipping.", scenarioName);
                continue;
            }

            String operationStr = mapOperation(avroCond.getOperation());
            String typeStr = mapConditionType(avroCond.getType());

            Optional<Condition> condOpt = conditionRepository.findByTypeAndOperationAndValue(typeStr, operationStr, value);
            Condition cond = condOpt.orElseGet(() -> {
                Condition c = new Condition();
                c.setType(typeStr);
                c.setOperation(operationStr);
                c.setValue(value);
                return conditionRepository.save(c);
            });

            String sensorId = avroCond.getSensorId();
            Sensor sensor = sensorRepository.findByIdAndHubId(sensorId, hub.getId())
                    .orElseThrow(() -> new IllegalStateException(
                            "Sensor " + sensorId + " not found for hub " + hub.getId()));

            ScenarioConditionId linkId = new ScenarioConditionId();
            linkId.setScenarioId(scenarioId);
            linkId.setSensorId(sensor.getId());
            linkId.setConditionId(cond.getId());

            ScenarioCondition link = new ScenarioCondition();
            link.setId(linkId);
            link.setScenario(scenario);
            link.setSensor(sensor);
            link.setCondition(cond);
            conditionLinks.add(link);
        }
        scenario.getConditions().addAll(conditionLinks);

        scenarioActionRepository.deleteByScenarioId(scenarioId);
        scenario.getActions().clear();

        List<ScenarioAction> actionLinks = new ArrayList<>();
        for (DeviceActionAvro avroAct : event.getActions()) {
            Object rawValueObj = avroAct.getValue();
            Integer finalActionValue;

            if (rawValueObj == null) {
                finalActionValue = 0;
                log.warn("Action for scenario {} has no value, using default 0.", scenarioName);
            } else {
                // union { null, int } -> если не null, то это Integer
                if (rawValueObj instanceof Integer i) {
                    finalActionValue = i;
                } else {
                    // на всякий случай, если вдруг придёт что-то неожиданное
                    log.error("Unexpected value type for action: {}", rawValueObj.getClass());
                    finalActionValue = 0;
                }
            }

            String actionTypeStr = mapActionType(avroAct.getType());

            Optional<Action> actOpt = actionRepository.findByTypeAndValue(actionTypeStr, finalActionValue);
            Action action = actOpt.orElseGet(() -> {
                Action a = new Action();
                a.setType(actionTypeStr);
                a.setValue(finalActionValue);
                return actionRepository.save(a);
            });

            String sensorId = avroAct.getSensorId();
            Sensor sensor = sensorRepository.findByIdAndHubId(sensorId, hub.getId())
                    .orElseThrow(() -> new IllegalStateException(
                            "Sensor " + sensorId + " not found for hub " + hub.getId()));

            ScenarioActionId linkId = new ScenarioActionId();
            linkId.setScenarioId(scenarioId);
            linkId.setSensorId(sensor.getId());
            linkId.setActionId(action.getId());

            ScenarioAction link = new ScenarioAction();
            link.setId(linkId);
            link.setScenario(scenario);
            link.setSensor(sensor);
            link.setAction(action);
            actionLinks.add(link);
        }
        scenario.getActions().addAll(actionLinks);

        scenarioRepository.save(scenario);
        log.info("Scenario saved: id={}, hub={}, name={}", scenario.getId(), hub.getId(), scenarioName);
    }

    @Transactional
    public void handleScenarioRemoved(Hub hub, ScenarioRemovedEventAvro event) {
        String name = event.getName();
        Optional<Scenario> scenarioOpt = scenarioRepository.findByHubIdAndName(hub.getId(), name);
        scenarioOpt.ifPresent(scenario -> {
            Long id = scenario.getId();
            scenarioConditionRepository.deleteByScenarioId(id);
            scenarioActionRepository.deleteByScenarioId(id);
            scenarioRepository.delete(scenario);
            log.info("Scenario removed: hub={}, name={}", hub.getId(), name);
        });
    }

    // Маппинг операций из Avro enum в строки для БД
    private String mapOperation(ConditionOperationAvro op) {
        return switch (op) {
            case EQUALS -> "==";
            case GREATER_THAN -> ">";
            case LOWER_THAN -> "<";
            default -> throw new IllegalArgumentException("Unsupported operation: " + op);
        };
    }

    private String mapConditionType(ConditionTypeAvro type) {
        return switch (type) {
            case MOTION -> "motion";
            case LUMINOSITY -> "luminosity";
            case SWITCH -> "switch";
            case TEMPERATURE -> "temperature";
            case CO2LEVEL -> "co2";
            case HUMIDITY -> "humidity";
            default -> type.name().toLowerCase();
        };
    }

    private String mapActionType(ActionTypeAvro type) {
        return switch (type) {
            case ACTIVATE -> "ACTIVATE";
            case DEACTIVATE -> "DEACTIVATE";
            case INVERSE -> "INVERSE";
            case SET_VALUE -> "SET_VALUE";
            default -> type.name();
        };
    }

    // Обработка union { null, int, boolean } из твоей схемы Avro
    private Integer extractValueFromAvroUnion(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean b) {
            return b ? 1 : 0;
        }
        if (value instanceof Integer i) {
            return i;
        }
        log.warn("Unexpected value type in ScenarioConditionAvro.getValue(): {}", value.getClass());
        return null;
    }
}
