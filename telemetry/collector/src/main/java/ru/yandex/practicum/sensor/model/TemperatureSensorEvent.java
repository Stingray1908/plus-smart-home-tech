package ru.yandex.practicum.sensor.model;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import ru.yandex.practicum.sensor.enums.SensorEventType;

@Setter
@Getter
@ToString

public class TemperatureSensorEvent extends SensorEvent {
    private int temperatureC;
    private int temperatureF;

    public TemperatureSensorEvent(String id, String hubId,
                                  int temperatureC, int temperatureF) {
        super(id, hubId);
        this.temperatureC = temperatureC;
        this.temperatureF = temperatureF;
    }

    @Override
    public SensorEventType getType() {
        return SensorEventType.TEMPERATURE_SENSOR_EVENT;
    }
}
