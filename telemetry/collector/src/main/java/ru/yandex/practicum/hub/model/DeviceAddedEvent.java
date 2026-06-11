
package ru.yandex.practicum.hub.model;

import lombok.Getter;
import lombok.Setter;
import ru.yandex.practicum.hub.enums.DeviceType;
import ru.yandex.practicum.hub.enums.HubEventType;

@Getter
@Setter
public class DeviceAddedEvent extends BaseHubEvent {
    private String id;
    private DeviceType deviceType;

    @Override
    public HubEventType getType() {
        return HubEventType.DEVICE_ADDED;
    }
}
