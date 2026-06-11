package ru.yandex.practicum.hub.rest;

import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import ru.yandex.practicum.hub.model.BaseHubEvent;

@RestController
@RequestMapping("/events/hubs")
@Slf4j
@Validated
public class HubEventController {

    private final HubEventService hubEventService;

    public HubEventController(HubEventService hubEventService) {
        this.hubEventService = hubEventService;
    }

    /**
     * Эндпоинт для обработки событий от хаба
     *
     * @param hubEvent объект события хаба (DEVICE_ADDED, DEVICE_REMOVED и т. д.)
     * @return HTTP 200 OK при успешной обработке
     */
    @PostMapping
    public ResponseEntity<Void> handleHubEvent(
            @RequestBody @Valid BaseHubEvent hubEvent) {

        log.info("Получено событие хаба: type={}, hubId={}",
                hubEvent.getType(), hubEvent.getHubId());

        hubEventService.processHubEvent(hubEvent);

        log.debug("Событие хаба успешно обработано: hubId={}, type={}",
                hubEvent.getHubId(), hubEvent.getType());
        return ResponseEntity.ok().build();
    }
}
