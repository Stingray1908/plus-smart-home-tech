package ru.yandex.practicum.hub.rest;

import ru.yandex.practicum.hub.model.HubEvent;

/**
 * Сервис для обработки событий хаба и отправки их в Kafka.
 */
public interface HubEventService {

    /**
     * Обрабатывает событие хаба: преобразует в формат Avro и отправляет в Kafka.
     *
     * @param event событие хаба для обработки
     * @throws IllegalArgumentException если тип события не поддерживается
     */
    void processHubEvent(HubEvent event);
}
