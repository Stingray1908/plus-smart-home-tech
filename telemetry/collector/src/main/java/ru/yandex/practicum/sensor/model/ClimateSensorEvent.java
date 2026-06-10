package ru.yandex.practicum.sensor.model;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import ru.yandex.practicum.sensor.enums.SensorEventType;

@Setter
@Getter
@ToString

public class ClimateSensorEvent extends SensorEvent {
    private int temperatureC;
    private int humidity;
    private int co2Level;

    public ClimateSensorEvent(String id, String hubId, int temperatureC, int humidity, int co2Level) {
        super(id, hubId);
        this.temperatureC = temperatureC;
        this.humidity = humidity;
        this.co2Level = co2Level;
    }

    @Override
    public SensorEventType getType() {
        return SensorEventType.CLIMATE_SENSOR_EVENT;
    }
}

