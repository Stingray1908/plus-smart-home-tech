package ru.yandex.practicum.hub.model;

public class DeviceRemovedEvent extends BaseHubEvent {
    private String id;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    @Override
    public String toString() {
        return "DeviceRemovedEvent{" +
                "hubId='" + getHubId() + "'" +
                ", timestamp='" + getTimestamp() + "'" +
                ", type='" + getType() + "'" +
                ", id='" + id + "'" +
                '}';
    }
}

