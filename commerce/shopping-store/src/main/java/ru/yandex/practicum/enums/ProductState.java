package ru.yandex.practicum.enums;

public enum ProductState {
    ACTIVE,
    DEACTIVATE;

    private static final String ALLOWED_VALUES = "ACTIVE, DEACTIVATE";

    public static ProductState fromString(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "Состояние товара не может быть пустым. Доступные значения: " + ALLOWED_VALUES
            );
        }

        try {
            return valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Неверное состояние: '" + value + "'. Доступные значения: " + ALLOWED_VALUES,
                    e
            );
        }
    }
}
