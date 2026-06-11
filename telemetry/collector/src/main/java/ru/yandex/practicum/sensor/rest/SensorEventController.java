package ru.yandex.practicum.sensor.rest;

import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import ru.yandex.practicum.sensor.model.SensorEvent;

@RestController
@RequestMapping("/events")
@Slf4j
@Validated
public class SensorEventController {

    private final SensorEventService sensorEventService;

    public SensorEventController(SensorEventService sensorEventService) {
        this.sensorEventService = sensorEventService;
    }

    /**
     * Эндпоинт для обработки событий от датчиков
     *
     * @param sensorEvent объект события датчика (полиморфный: может быть любым подтипом SensorEvent)
     * @return HTTP 200 OK при успешной обработке
     */
    @PostMapping("/sensors")
    public ResponseEntity<Void> handleSensorEvent(
            @RequestBody @Valid SensorEvent sensorEvent) {

        log.info("Получены показания датчика: type={}, id={}",
                sensorEvent.getType(), sensorEvent.getId());

        sensorEventService.processSensor(sensorEvent);

        log.debug("Событие датчика успешно обработано: {}", sensorEvent.getId());
        return ResponseEntity.ok().build();
    }
}

