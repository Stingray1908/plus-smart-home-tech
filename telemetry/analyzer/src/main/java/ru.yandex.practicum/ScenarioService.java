package ru.yandex.practicum;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.yandex.practicum.entity.*;
import ru.yandex.practicum.enums.ActionType;
import ru.yandex.practicum.kafka.telemetry.event.*;
import ru.yandex.practicum.repository.*;

import java.util.ArrayList;
import java.util.List;

@RequiredArgsConstructor
@Service
@Slf4j
public class ScenarioService {

    private final ScenarioRepository scenarioRepository;
    private final ConditionRepository conditionRepository;
    private final ActionRepository actionRepository;
    private final SensorRepository sensorRepository;
    private final ScenarioConditionRepository scenarioConditionRepository;
    private final ScenarioActionRepository scenarioActionRepository;

    @Transactional
    public void handleScenarioAdded(Hub hub, ScenarioAddedEventAvro event) {
        String scenarioName = event.getName();
        log.info("Processing SCENARIO_ADDED: hub={}, name={}", hub.getId(), scenarioName);

        Scenario scenario = scenarioRepository.findByHubIdAndName(hub.getId(), scenarioName)
                .orElseGet(() -> {
                    Scenario s = new Scenario();
                    s.setHub(hub);
                    s.setName(scenarioName);
                    return s;
                });

        scenario = scenarioRepository.save(scenario); // получаем ID
        Long scenarioId = scenario.getId();

        // Сначала удаляем старые связи в БД
        scenarioConditionRepository.deleteByScenarioId(scenarioId);
        scenarioActionRepository.deleteByScenarioId(scenarioId);

        // Очищаем коллекции (если они загружены)
        scenario.getConditions().clear();
        scenario.getActions().clear();

        List<ScenarioCondition> conditionLinks = new ArrayList<>();
        for (ScenarioConditionAvro avroCond : event.getConditions()) {
            Integer value = extractValueFromAvroUnion(avroCond.getValue());
            if (value == null) {
                log.warn("Condition for scenario {} has no value, skipping.", scenarioName);
                continue;
            }

            String operationStr = mapOperation(avroCond.getOperation());
            String typeStr = mapConditionType(avroCond.getType());

            Condition cond = conditionRepository.findByTypeAndOperationAndValue(typeStr, operationStr, value)
                    .orElseGet(() -> saveCondition(typeStr, operationStr, value));

            Sensor sensor = sensorRepository.findByIdAndHubId(avroCond.getSensorId(), hub.getId())
                    .orElseThrow(() -> new IllegalStateException(
                            "Sensor " + avroCond.getSensorId() + " not found for hub " + hub.getId()));

            ScenarioCondition link = new ScenarioCondition();
            ScenarioConditionId linkId = new ScenarioConditionId();
            linkId.setScenarioId(scenarioId);
            linkId.setSensorId(sensor.getId());
            linkId.setConditionId(cond.getId());
            link.setId(linkId);
            link.setScenario(scenario);
            link.setSensor(sensor);
            link.setCondition(cond);
            conditionLinks.add(link);
        }
        scenario.getConditions().addAll(conditionLinks);

        List<ScenarioAction> actionLinks = new ArrayList<>();
        for (DeviceActionAvro avroAct : event.getActions()) {
            Integer finalActionValue = (avroAct.getValue() instanceof Integer i)
                    ? i
                    : 0;
            if (avroAct.getValue() == null) {
                log.warn("Action for scenario {} has no value, using default 0.", scenarioName);
            } else if (!(avroAct.getValue() instanceof Integer)) {
                log.error("Unexpected value type for action: {}", avroAct.getValue().getClass());
            }

            ActionType actionType = mapActionType(avroAct.getType());
            Action action = actionRepository.findByTypeAndValue(actionType, finalActionValue)
                    .orElseGet(() -> saveAction(actionType, finalActionValue));

            Sensor sensor = sensorRepository.findByIdAndHubId(avroAct.getSensorId(), hub.getId())
                    .orElseThrow(() -> new IllegalStateException(
                            "Sensor " + avroAct.getSensorId() + " not found for hub " + hub.getId()));

            ScenarioAction link = new ScenarioAction();
            ScenarioActionId linkId = new ScenarioActionId();
            linkId.setScenarioId(scenarioId);
            linkId.setSensorId(sensor.getId());
            linkId.setActionId(action.getId());
            link.setId(linkId);
            link.setScenario(scenario);
            link.setSensor(sensor);
            link.setAction(action);
            actionLinks.add(link);
        }
        scenario.getActions().addAll(actionLinks);

        // Второй save не нужен: изменения в рамках транзакции и контекста персистентности
        log.info("Scenario saved: id={}, hub={}, name={}", scenario.getId(), hub.getId(), scenarioName);
    }

    @Transactional
    public void handleScenarioRemoved(Hub hub, ScenarioRemovedEventAvro event) {
        String name = event.getName();
        scenarioRepository.findByHubIdAndName(hub.getId(), name).ifPresent(scenario -> {
            Long id = scenario.getId();
            scenarioConditionRepository.deleteByScenarioId(id);
            scenarioActionRepository.deleteByScenarioId(id);
            scenarioRepository.delete(scenario);
            log.info("Scenario removed: hub={}, name={}", hub.getId(), name);
        });
    }

    private Condition saveCondition(String type, String operation, Integer value) {
        Condition c = new Condition();
        c.setType(type);
        c.setOperation(operation);
        c.setValue(value);
        return conditionRepository.save(c);
    }

    private Action saveAction(ActionType type, Integer value) {
        Action a = new Action();
        a.setType(type);
        a.setValue(value);
        return actionRepository.save(a);
    }

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

    private ActionType mapActionType(ActionTypeAvro type) {
        return switch (type) {
            case ACTIVATE -> ActionType.ACTIVATE;
            case DEACTIVATE -> ActionType.DEACTIVATE;
            case INVERSE -> ActionType.ACTIVATE;
            case SET_VALUE -> ActionType.SET_TEMP;
            default -> throw new IllegalArgumentException("Unsupported action type: " + type);
        };
    }

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
