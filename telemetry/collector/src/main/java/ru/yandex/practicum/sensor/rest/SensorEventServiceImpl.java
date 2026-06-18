    package ru.yandex.practicum.sensor.rest;

    import lombok.NoArgsConstructor;
    import lombok.RequiredArgsConstructor;
    import lombok.extern.slf4j.Slf4j;
    import org.apache.avro.specific.SpecificRecordBase;
    import org.apache.kafka.clients.producer.KafkaProducer;
    import org.apache.kafka.clients.producer.ProducerRecord;
    import org.springframework.beans.factory.annotation.Autowired;
    import org.springframework.stereotype.Service;
    import ru.yandex.practicum.config.KafkaProperties;
    import ru.yandex.practicum.kafka.telemetry.event.*;
    import ru.yandex.practicum.sensor.enums.SensorEventType;
    import ru.yandex.practicum.sensor.model.*;

    @NoArgsConstructor
    @Service
    @Slf4j
    public class SensorEventServiceImpl implements SensorEventService {

        private KafkaProducer<String, SpecificRecordBase> producer;
        private KafkaProperties kafkaProperties;

        @Autowired
        public SensorEventServiceImpl(
                KafkaProducer<String, SpecificRecordBase> producer,
                KafkaProperties kafkaProperties) {
            this.producer = producer;
            this.kafkaProperties = kafkaProperties;
        }

        @Override
        public void processSensor(SensorEvent event) {
            SensorEventType eventType = event.getType();
            SensorEventPayload sensorEventPayload;

            switch (eventType) {
                case LIGHT_SENSOR_EVENT -> sensorEventPayload = convertLightSensorEvent((LightSensorEvent) event);
                case SWITCH_SENSOR_EVENT -> sensorEventPayload = convertSwitchSensorEvent((SwitchSensorEvent) event);
                case CLIMATE_SENSOR_EVENT -> sensorEventPayload = convertClimateSensorEvent((ClimateSensorEvent) event);
                case MOTION_SENSOR_EVENT -> sensorEventPayload = convertMotionSensorEvent((MotionSensorEvent) event);
                case TEMPERATURE_SENSOR_EVENT ->
                        sensorEventPayload = convertTemperatureSensorEvent((TemperatureSensorEvent) event);
                default -> throw new IllegalArgumentException("Unsupported event type: " + eventType);
            }

            SensorEventAvro sensorEventAvro = SensorEventAvro.newBuilder()
                    .setId(event.getId())
                    .setHubId(event.getHubId())
                    .setTimestamp(event.getTimestamp().toEpochMilli())
                    .setPayload(sensorEventPayload)
                    .build();

            ProducerRecord<String, SpecificRecordBase> record =
                    new ProducerRecord<>(kafkaProperties.getTopic().getSensorEvents(), event.getId(), sensorEventAvro);

            producer.send(record, (metadata, exception) -> {
                if (exception != null) {
                    log.error("Ошибка отправки в Kafka: {}", exception.getMessage(), exception);
                } else {
                    log.info("Показания датчика отправлены в Kafka: topic={}, partition={}, offset={}",
                            metadata.topic(), metadata.partition(), metadata.offset());
                }
            });
        }

        /**
         * Вспомогательный метод для создания SensorEventPayload из конкретной Avro‑записи.
         * Устраняет дублирование кода в конвертерах.
         */
        private SensorEventPayload createSensorEventPayload(SpecificRecordBase specificRecord) {
            return SensorEventPayload.newBuilder().setPayload(specificRecord).build();
        }

        private SensorEventPayload convertLightSensorEvent(LightSensorEvent event) {
            LightSensorAvro lightAvro = LightSensorAvro.newBuilder()
                    .setLinkQuality(event.getLinkQuality())
                    .setLuminosity(event.getLuminosity())
                    .build();
            return createSensorEventPayload(lightAvro);
        }

        private SensorEventPayload convertSwitchSensorEvent(SwitchSensorEvent event) {
            SwitchSensorAvro switchAvro = SwitchSensorAvro.newBuilder()
                    .setState(event.isState())
                    .build();
            return createSensorEventPayload(switchAvro);
        }

        private SensorEventPayload convertClimateSensorEvent(ClimateSensorEvent event) {
            ClimateSensorAvro climateAvro = ClimateSensorAvro.newBuilder()
                    .setTemperatureC(event.getTemperatureC())
                    .setHumidity(event.getHumidity())
                    .setCo2Level(event.getCo2Level())
                    .build();
            return createSensorEventPayload(climateAvro);
        }

        private SensorEventPayload convertMotionSensorEvent(MotionSensorEvent event) {
            MotionSensorAvro motionAvro = MotionSensorAvro.newBuilder()
                    .setLinkQuality(event.getLinkQuality())
                    .setMotion(event.isMotion())
                    .setVoltage(event.getVoltage())
                    .build();
            return createSensorEventPayload(motionAvro);
        }

        private SensorEventPayload convertTemperatureSensorEvent(TemperatureSensorEvent event) {
            TemperatureSensorAvro tempAvro = TemperatureSensorAvro.newBuilder()
                    .setId(event.getId())
                    .setHubId(event.getHubId())
                    .setTimestamp(event.getTimestamp().toEpochMilli())
                    .setTemperatureC(event.getTemperatureC())
                    .setTemperatureF(event.getTemperatureF())
                    .build();
            return createSensorEventPayload(tempAvro);
        }
    }
