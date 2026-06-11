package ru.yandex.practicum.hub.model;

public class DeviceAddedEvent extends BaseHubEvent {
    private String id;
    private String deviceType;

    // геттеры и сеттеры
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getDeviceType() { return deviceType; }
    public void setDeviceType(String deviceType) { this.deviceType = deviceType; }
}

