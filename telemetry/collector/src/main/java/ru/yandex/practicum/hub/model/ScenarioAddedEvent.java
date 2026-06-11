package ru.yandex.practicum.hub.model;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import ru.yandex.practicum.hub.enums.HubEventType;

import java.util.List;

@Getter
@Setter
@ToString
public class ScenarioAddedEvent extends HubEvent {
    private String name;
    private List<ScenarioCondition> conditions;
    private List<DeviceAction> actions;

    @Override
    public HubEventType getType() {
        return HubEventType.SCENARIO_ADDED;
    }
}
