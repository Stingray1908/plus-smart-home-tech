package ru.yandex.practicum.hub.model;

import lombok.Getter;
import lombok.Setter;
import ru.yandex.practicum.hub.enums.HubEventType;

@Getter
@Setter
public class ScenarioRemovedEvent extends BaseHubEvent {
    private String name;

    @Override
    public HubEventType getType() {
        return HubEventType.SCENARIO_REMOVED;
    }
}
