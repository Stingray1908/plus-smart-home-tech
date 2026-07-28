package ru.yandex.practicum.enums;

public enum ProductCategory {
    LIGHTING,
    CONTROL,
    SENSORS;

    public static ProductCategory fromString(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "Категория не может быть пустой. Доступные значения: LIGHTING, CONTROL, SENSORS"
            );
        }

        try {
            return valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Неверная категория: '" + value + "'. Доступные значения: LIGHTING, CONTROL, SENSORS",
                    e
            );
        }
    }
    }
