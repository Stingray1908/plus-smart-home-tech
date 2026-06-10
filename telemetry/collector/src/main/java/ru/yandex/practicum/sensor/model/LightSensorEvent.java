package ru.yandex.practicum.sensor.model;

import lombok.Getter;
import lombok.ToString;
import ru.yandex.practicum.sensor.enums.SensorEventType;

import java.time.Instant;

@Getter
@ToString

public class LightSensorEvent extends SensorEvent {
    private Integer linkQuality;
    private Integer luminosity;

    public LightSensorEvent(String id, String hubId, Integer linkQuality, Integer luminosity) {
        super(id, hubId);
        this.linkQuality = linkQuality;
        this.luminosity = luminosity;
    }

    @Override
    public SensorEventType getType() {
        return SensorEventType.LIGHT_SENSOR_EVENT;
    }
}
