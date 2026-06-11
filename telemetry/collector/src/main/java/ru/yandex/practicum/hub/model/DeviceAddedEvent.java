package ru.yandex.practicum.hub.model;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import ru.yandex.practicum.hub.enums.DeviceType;
import ru.yandex.practicum.hub.enums.HubEventType;

@Getter
@Setter
@ToString
public class DeviceAddedEvent extends HubEvent {
    private String id;
    private DeviceType deviceType;

    @Override
    public HubEventType getType() {
        return HubEventType.DEVICE_ADDED;
    }
}
