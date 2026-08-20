package ru.yandex.practicum.dto;

public enum OrderStatus {
    NEW(false, false),
    ON_PAYMENT(true, true),
    ON_DELIVERY(true, false),
    DONE(true, false),
    DELIVERED(false, false),
    ASSEMBLED(true, false),
    PAID(true, false),
    COMPLETED(false, false),
    DELIVERY_FAILED(true, false),
    ASSEMBLY_FAILED(true, false),
    PAYMENT_FAILED(true, false),
    PRODUCT_RETURNED(false, false),
    CANCELED(false, false);

    private final boolean canReturn;
    private final boolean canPay;

    OrderStatus(boolean canReturn, boolean canPay) {
        this.canReturn = canReturn;
        this.canPay = canPay;
    }

    public boolean canReturn() {
        return canReturn;
    }

    public boolean canPay() {
        return canPay;
    }

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
