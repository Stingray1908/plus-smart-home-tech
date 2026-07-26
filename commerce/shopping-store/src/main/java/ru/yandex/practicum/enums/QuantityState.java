package ru.yandex.practicum.enums;

public enum QuantityState {
    ENDED,
    FEW,
    ENOUGH,
    MANY;

    private static final String ALLOWED_VALUES = "ENDED, FEW, ENOUGH, MANY";

    public static QuantityState fromString(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "Состояние количества не может быть пустым. Доступные значения: " + ALLOWED_VALUES
            );
        }

        try {
            return valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Неверное состояние количества: '" + value + "'. Доступные значения: " + ALLOWED_VALUES,
                    e
            );
        }
    }
}
