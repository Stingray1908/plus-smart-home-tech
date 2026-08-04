package ru.yandex.practicum.dto;

public enum OrderStatus  {
    NEW,
    ON_PAYMENT,
    ON_DELIVERY,
    DONE,
    DELIVERED,
    ASSEMBLED,
    PAID,
    COMPLETED,
    DELIVERY_FAILED,
    ASSEMBLY_FAILED,
    PAYMENT_FAILED,
    PRODUCT_RETURNED,
    CANCELED;

    public static OrderStatus  fromString(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "Значение не может быть пустым"
            );
        }

        try {
            return valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Неверное значение: '" + value + "'.",
                    e
            );
        }
    }
}
