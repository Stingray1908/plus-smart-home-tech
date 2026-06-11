package ru.yandex.practicum.hub.model;

public class DeviceAction {
    private String sensorId;
    private String type;
    private Integer value;

    public String getSensorId() {
        return sensorId;
    }

    public void setSensorId(String sensorId) {
        this.sensorId = sensorId;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Integer getValue() {
        return value;
    }

    public void setValue(Integer value) {
        this.value = value;
    }

    @Override
    public String toString() {
        return "DeviceAction{" +
                "sensorId='" + sensorId + "'" +
                ", type='" + type + "'" +
                ", value=" + value +
                '}';
    }
}

