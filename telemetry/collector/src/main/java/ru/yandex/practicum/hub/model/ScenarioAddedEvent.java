package ru.yandex.practicum.hub.model;

import java.util.List;

public class ScenarioAddedEvent extends BaseHubEvent {
    private String name;
    private List<ScenarioCondition> conditions;
    private List<DeviceAction> actions;

    // геттеры и сеттеры
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public List<ScenarioCondition> getConditions() { return conditions; }
    public void setConditions(List<ScenarioCondition> conditions) { this.conditions = conditions; }

    public List<DeviceAction> getActions() { return actions; }
    public void setActions(List<DeviceAction> actions) { this.actions = actions; }
}

