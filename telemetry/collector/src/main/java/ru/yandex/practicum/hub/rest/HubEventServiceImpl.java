package ru.yandex.practicum.hub.rest;

import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.avro.specific.SpecificRecordBase;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ru.yandex.practicum.config.KafkaProperties;
import ru.yandex.practicum.hub.enums.HubEventType;
import ru.yandex.practicum.hub.model.*;
import ru.yandex.practicum.kafka.telemetry.event.*;

import java.util.List;
import java.util.stream.Collectors;

@NoArgsConstructor
@Service
@Slf4j
public class HubEventServiceImpl implements HubEventService {

    private KafkaProducer<String, SpecificRecordBase> producer;
    private KafkaProperties kafkaProperties;

    @Autowired
    public HubEventServiceImpl(
            KafkaProducer<String, SpecificRecordBase> producer,
            KafkaProperties kafkaProperties) {
        this.producer = producer;
        this.kafkaProperties = kafkaProperties;
    }
    @Override
    public void processHubEvent(HubEvent event) {
        HubEventType eventType = event.getType();
        Object payload;

        switch (eventType) {
            case DEVICE_ADDED -> payload = convertDeviceAddedEvent((DeviceAddedEvent) event);
            case DEVICE_REMOVED -> payload = convertDeviceRemovedEvent((DeviceRemovedEvent) event);
            case SCENARIO_ADDED -> payload = convertScenarioAddedEvent((ScenarioAddedEvent) event);
            case SCENARIO_REMOVED -> payload = convertScenarioRemovedEvent((ScenarioRemovedEvent) event);
            default -> throw new IllegalArgumentException("Unsupported hub event type: " + eventType);
        }

        HubEventAvro hubEventAvro = HubEventAvro.newBuilder()
                .setHubId(event.getHubId())
                .setTimestamp(event.getTimestamp())
                .setPayload(payload)
                .build();

        ProducerRecord<String, SpecificRecordBase> record =
                new ProducerRecord<>(kafkaProperties.getTopic().getHubEvents(), event.getHubId(), hubEventAvro);

        producer.send(record, (metadata, exception) -> {
            if (exception != null) {
                log.error("Ошибка отправки в Kafka: {}", exception.getMessage(), exception);
            } else {
                log.info("Событие хаба отправлено в Kafka: topic={}, partition={}, offset={}",
                        metadata.topic(), metadata.partition(), metadata.offset());
            }
        });
    }

    private DeviceAddedEventAvro convertDeviceAddedEvent(DeviceAddedEvent event) {
        return DeviceAddedEventAvro.newBuilder()
                .setId(event.getId())
                .setType(DeviceTypeAvro.valueOf(event.getDeviceType().name()))
                .build();
    }

    private DeviceRemovedEventAvro convertDeviceRemovedEvent(DeviceRemovedEvent event) {
        return DeviceRemovedEventAvro.newBuilder()
                .setId(event.getId())
                .build();
    }

    private ScenarioAddedEventAvro convertScenarioAddedEvent(ScenarioAddedEvent event) {
        List<ScenarioConditionAvro> conditionsAvro = event.getConditions().stream()
                .map(this::convertScenarioCondition)
                .collect(Collectors.toList());

        List<DeviceActionAvro> actionsAvro = event.getActions().stream()
                .map(this::convertDeviceAction)
                .collect(Collectors.toList());

        return ScenarioAddedEventAvro.newBuilder()
                .setName(event.getName())
                .setConditions(conditionsAvro)
                .setActions(actionsAvro)
                .build();
    }

    private ScenarioRemovedEventAvro convertScenarioRemovedEvent(ScenarioRemovedEvent event) {
        return ScenarioRemovedEventAvro.newBuilder()
                .setName(event.getName())
                .build();
    }

    private ScenarioConditionAvro convertScenarioCondition(ScenarioCondition condition) {
        Object value = condition.getValue() != null
                ? condition.getValue()
                : null;

        return ScenarioConditionAvro.newBuilder()
                .setSensorId(condition.getSensorId())
                .setType(ConditionTypeAvro.valueOf(condition.getType().name()))
                .setOperation(ConditionOperationAvro.valueOf(condition.getOperation().name()))
                .setValue(value)
                .build();
    }

    private DeviceActionAvro convertDeviceAction(DeviceAction action) {
        Integer value = action.getValue() != null
                ? action.getValue()
                : null;

        return DeviceActionAvro.newBuilder()
                .setSensorId(action.getSensorId())
                .setType(ActionTypeAvro.valueOf(action.getType().name()))
                .setValue(value)
                .build();
    }
}
