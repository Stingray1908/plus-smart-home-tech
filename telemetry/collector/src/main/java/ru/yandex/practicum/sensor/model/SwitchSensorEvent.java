package ru.yandex.practicum.sensor.model;


import lombok.Getter;
import lombok.ToString;
import ru.yandex.practicum.sensor.enums.SensorEventType;

import java.time.Instant;

@Getter
@ToString

public class SwitchSensorEvent extends SensorEvent {
    private boolean state;

    public SwitchSensorEvent(String id, String hubId, boolean state) {
        super(id, hubId);
        this.state = state;
    }

    @Override
    public SensorEventType getType() {
        return SensorEventType.SWITCH_SENSOR_EVENT;
    }
}
