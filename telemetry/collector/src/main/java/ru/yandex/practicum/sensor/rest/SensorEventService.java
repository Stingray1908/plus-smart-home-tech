package ru.yandex.practicum.sensor.rest;

import ru.yandex.practicum.sensor.model.SensorEvent;

/**
 * Сервис для обработки событий датчиков и отправки их в Kafka.
 */
public interface SensorEventService {

    /**
     * Обрабатывает событие датчика: преобразует в формат Avro и отправляет в Kafka.
     *
     * @param event событие датчика для обработки
     * @throws IllegalArgumentException если тип события не поддерживается
     */
    void processSensor(SensorEvent event);
}
