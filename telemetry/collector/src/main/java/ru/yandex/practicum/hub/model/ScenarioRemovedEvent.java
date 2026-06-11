package ru.yandex.practicum.hub.model;

public class ScenarioRemovedEvent extends BaseHubEvent {
    private String name;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return "ScenarioRemovedEvent{" +
                "hubId='" + getHubId() + "'" +
                ", timestamp='" + getTimestamp() + "'" +
                ", type='" + getType() + "'" +
                ", name='" + name + "'" +
                '}';
    }
}

