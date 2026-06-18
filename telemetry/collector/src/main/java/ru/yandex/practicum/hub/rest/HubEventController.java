package ru.yandex.practicum.hub.rest;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.yandex.practicum.hub.model.HubEvent;

@RestController
@RequestMapping("/events/hubs")
@RequiredArgsConstructor
@Slf4j
@Validated
public class HubEventController {

    private final HubEventServiceImpl hubEventService;

    /**
     * Эндпоинт для обработки событий от хаба
     *
     * @param hubEvent объект события хаба (DEVICE_ADDED, DEVICE_REMOVED и т. д.)
     * @return HTTP 200 OK при успешной обработке
     */
    @PostMapping
    public ResponseEntity<Void> handleHubEvent(
            @RequestBody @Valid HubEvent hubEvent) {

        log.info("Получено событие хаба: type={}, hubId={}",
                hubEvent.getType(), hubEvent.getHubId());

        hubEventService.processHubEvent(hubEvent);

        log.debug("Событие хаба успешно обработано: hubId={}, type={}",
                hubEvent.getHubId(), hubEvent.getType());
        return ResponseEntity.ok().build();
    }
}
