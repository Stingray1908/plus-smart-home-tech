
package ru.yandex.practicum.hub.model;

import lombok.Getter;
import lombok.Setter;
import ru.yandex.practicum.hub.enums.HubEventType;

@Getter
@Setter
public class DeviceRemovedEvent extends BaseHubEvent {
    private String id;

    @Override
    public HubEventType getType() {
        return HubEventType.DEVICE_REMOVED;
    }
}
