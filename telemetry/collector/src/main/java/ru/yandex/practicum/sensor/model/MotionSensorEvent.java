package ru.yandex.practicum.sensor.model;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import ru.yandex.practicum.sensor.enums.SensorEventType;

@Setter
@Getter
@ToString
public class MotionSensorEvent extends SensorEvent {
    private int linkQuality;
    private boolean motion;
    private int voltage;

    public MotionSensorEvent(String id, String hubId,
                             int linkQuality, boolean motion, int voltage) {
        super(id, hubId);
        this.linkQuality = linkQuality;
        this.motion = motion;
        this.voltage = voltage;
    }

    @Override
    public SensorEventType getType() {
        return SensorEventType.MOTION_SENSOR_EVENT;
    }
}
