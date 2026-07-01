package ru.yandex.practicum;

import com.google.protobuf.Empty;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import net.devh.boot.grpc.server.service.GrpcService;
import org.apache.avro.specific.SpecificRecordBase;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.yandex.practicum.config.KafkaProperties;
import ru.yandex.practicum.grpc.telemetry.collector.CollectorControllerGrpc;
import ru.yandex.practicum.grpc.telemetry.event.*;
import ru.yandex.practicum.kafka.telemetry.event.*;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@GrpcService
@RequiredArgsConstructor
public class CollectorGrpcService extends CollectorControllerGrpc.CollectorControllerImplBase {

    private static final Logger log = LoggerFactory.getLogger(CollectorGrpcService.class);
    private static final String SENSORS_TOPIC = "telemetry.sensors.v1";
    private static final String HUBS_TOPIC = "telemetry.hubs.v1";

    private final KafkaProducer<String, SpecificRecordBase> kafkaProducer;
    // private final KafkaProperties kafkaProperties; // если не нужен — можно убрать

    // Этот метод теперь точно соответствует CollectHubEvent из .proto
    @Override
    public void collectHubEvent(HubEventProto request, StreamObserver<Empty> responseObserver) {
        try {
            String hubId = request.getHubId();
            if (hubId == null || hubId.isBlank()) {
                throw new IllegalArgumentException("hubId обязателен");
            }

            log.info("gRPC collectHubEvent вызван: hubId={}", hubId);

            // Конвертируем google.protobuf.Timestamp в java.time.Instant
            Instant timestamp = Instant.ofEpochSecond(
                    request.getTimestamp().getSeconds(),
                    request.getTimestamp().getNanos()
            );

            SpecificRecordBase payloadAvro;

            switch (request.getPayloadCase()) {
                case DEVICE_ADDED:
                    payloadAvro = convertDeviceAddedEvent(request.getDeviceAdded());
                    break;
                case DEVICE_REMOVED:
                    payloadAvro = convertDeviceRemovedEvent(request.getDeviceRemoved());
                    break;
                case SCENARIO_ADDED:
                    payloadAvro = convertScenarioAddedEvent(request.getScenarioAdded());
                    break;
                case SCENARIO_REMOVED:
                    payloadAvro = convertScenarioRemovedEvent(request.getScenarioRemoved());
                    break;
                case PAYLOAD_NOT_SET:
                    throw new IllegalArgumentException("payload не установлен в HubEventProto");
                default:
                    throw new IllegalArgumentException("Неизвестный тип payload: " + request.getPayloadCase());
            }

            HubEventAvro hubEventAvro = HubEventAvro.newBuilder()
                    .setHubId(hubId)
                    .setTimestamp(timestamp)
                    .setPayload(payloadAvro)
                    .build();

            ProducerRecord<String, SpecificRecordBase> record =
                    new ProducerRecord<>(HUBS_TOPIC, hubId, hubEventAvro);

            kafkaProducer.send(record, (metadata, exception) -> {
                if (exception != null) {
                    log.error("Ошибка отправки в Kafka: {}", exception.getMessage(), exception);
                    // Если нужно, можно как-то сообщить об ошибке клиенту, но gRPC-ответ уже отправлен
                } else {
                    log.info("Событие хаба отправлено в Kafka: topic={}, partition={}, offset={}",
                            metadata.topic(), metadata.partition(), metadata.offset());
                }
            });

            responseObserver.onNext(Empty.getDefaultInstance());
            responseObserver.onCompleted();

        } catch (Exception e) {
            log.error("Ошибка обработки collectHubEvent", e);
            responseObserver.onError(new StatusRuntimeException(
                    Status.INTERNAL.withDescription(e.getMessage()).withCause(e)));
        }
    }


    // Этот метод точно соответствует CollectSensorEvent из .proto
    @Override
    public void collectSensorEvent(SensorEventProto request, StreamObserver<Empty> responseObserver) {
        try {
            String sensorId = request.getId();
            String hubId = request.getHubId();

            if (sensorId == null || sensorId.isBlank()) {
                throw new IllegalArgumentException("sensorId обязателен");
            }
            if (hubId == null || hubId.isBlank()) {
                throw new IllegalArgumentException("hubId обязателен");
            }

            log.trace("gRPC collectSensorEvent: hubId={}, sensorId={}", hubId, sensorId);

            SensorEventPayload payload = new SensorEventPayload();

            switch (request.getPayloadCase()) {
                case MOTION_SENSOR -> {
                    var m = request.getMotionSensor();
                    var motionAvro = MotionSensorAvro.newBuilder()
                            .setLinkQuality(m.getLinkQuality())
                            .setMotion(m.getMotion())
                            .setVoltage(m.getVoltage())
                            .build();
                    payload.setPayload(motionAvro);
                }
                case TEMPERATURE_SENSOR -> {
                    var t = request.getTemperatureSensor();
                    var tempAvro = TemperatureSensorAvro.newBuilder()
                            .setId(sensorId)
                            .setHubId(hubId)
                            .setTimestamp(System.currentTimeMillis())
                            .setTemperatureC(t.getTemperatureC())
                            .setTemperatureF(t.getTemperatureF())
                            .build();
                    payload.setPayload(tempAvro);
                }
                case LIGHT_SENSOR -> {
                    var l = request.getLightSensor();
                    var lightAvro = LightSensorAvro.newBuilder()
                            .setLinkQuality(l.getLinkQuality())
                            .setLuminosity(l.getLuminosity())
                            .build();
                    payload.setPayload(lightAvro);
                }
                case CLIMATE_SENSOR -> {
                    var c = request.getClimateSensor();
                    var climateAvro = ClimateSensorAvro.newBuilder()
                            .setTemperatureC(c.getTemperatureC())
                            .setHumidity(c.getHumidity())
                            .setCo2Level(c.getCo2Level())
                            .build();
                    payload.setPayload(climateAvro);
                }
                case SWITCH_SENSOR -> {
                    var s = request.getSwitchSensor();
                    var switchAvro = SwitchSensorAvro.newBuilder()
                            .setState(s.getState())
                            .build();
                    payload.setPayload(switchAvro);
                }
                default -> log.warn("Неизвестный тип payload для sensorId={}", sensorId);
            }

            long tsMillis = request.hasTimestamp()
                    ? (request.getTimestamp().getSeconds() * 1000L + request.getTimestamp().getNanos() / 1_000_000)
                    : System.currentTimeMillis();

            SensorEventAvro avroEvent = SensorEventAvro.newBuilder()
                    .setId(sensorId)
                    .setHubId(hubId)
                    .setTimestamp(tsMillis)
                    .setPayload(payload)
                    .build();

            String key = hubId;
            ProducerRecord<String, SpecificRecordBase> record = new ProducerRecord<>(SENSORS_TOPIC, key, avroEvent);

            kafkaProducer.send(record, (metadata, exception) -> {
                if (exception != null) {
                    log.error("Асинхронная ошибка отправки в Kafka: topic={}, key={}", SENSORS_TOPIC, key, exception);
                } else {
                    log.debug("Отправлено: topic={}, partition={}, offset={}",
                            SENSORS_TOPIC, metadata.partition(), metadata.offset());
                }
            });

            responseObserver.onNext(Empty.getDefaultInstance());
            responseObserver.onCompleted();

        } catch (Exception e) {
            log.error("Ошибка обработки collectSensorEvent", e);
            responseObserver.onError(new StatusRuntimeException(
                    Status.INTERNAL.withDescription(e.getMessage()).withCause(e)));
        }
    }

    @Override
    public void getStatus(Empty request, StreamObserver<Empty> responseObserver) {
        responseObserver.onNext(Empty.getDefaultInstance());
        responseObserver.onCompleted();
    }

    private DeviceAddedEventAvro convertDeviceAddedEvent(DeviceAddedEventProto proto) {
        return DeviceAddedEventAvro.newBuilder()
                .setId(proto.getId())
                .setType(DeviceTypeAvro.valueOf(proto.getType().name()))
                .build();
    }

    private DeviceRemovedEventAvro convertDeviceRemovedEvent(DeviceRemovedEventProto proto) {
        return DeviceRemovedEventAvro.newBuilder()
                .setId(proto.getId())
                .build();
    }

    private ScenarioAddedEventAvro convertScenarioAddedEvent(ScenarioAddedEventProto proto) {
        List<ScenarioConditionAvro> conditionsAvro = proto.getConditionList().stream()
                .map(this::convertScenarioCondition)
                .collect(Collectors.toList());

        List<DeviceActionAvro> actionsAvro = proto.getActionList().stream()
                .map(this::convertDeviceAction)
                .collect(Collectors.toList());

        return ScenarioAddedEventAvro.newBuilder()
                .setName(proto.getName())
                .setConditions(conditionsAvro)
                .setActions(actionsAvro)
                .build();
    }

    private ScenarioRemovedEventAvro convertScenarioRemovedEvent(ScenarioRemovedEventProto proto) {
        return ScenarioRemovedEventAvro.newBuilder()
                .setName(proto.getName())
                .build();
    }

    private ScenarioConditionAvro convertScenarioCondition(ScenarioConditionProto proto) {
        Object value = switch (proto.getValueCase()) {
            case BOOL_VALUE -> proto.getBoolValue();
            case INT_VALUE -> proto.getIntValue();
            case VALUE_NOT_SET -> null;
        };

        return ScenarioConditionAvro.newBuilder()
                .setSensorId(proto.getSensorId())
                .setType(ConditionTypeAvro.valueOf(proto.getType().name()))
                .setOperation(ConditionOperationAvro.valueOf(proto.getOperation().name()))
                .setValue(value)
                .build();
    }

    private DeviceActionAvro convertDeviceAction(DeviceActionProto proto) {
        Integer value = proto.hasValue() ? proto.getValue() : null;

        return DeviceActionAvro.newBuilder()
                .setSensorId(proto.getSensorId())
                .setType(ActionTypeAvro.valueOf(proto.getType().name()))
                .setValue(value)
                .build();
    }

}
