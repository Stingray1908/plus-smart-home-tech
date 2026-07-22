package ru.yandex.practicum.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class NoSpecifiedProductInWarehouseException extends RuntimeException {

    private final int httpStatus;
    private final String userMessage;

    public NoSpecifiedProductInWarehouseException(String message, String userMessage, int httpStatus, Throwable cause) {
        super(message, cause);
        this.userMessage = userMessage;
        this.httpStatus = httpStatus;
    }

    public NoSpecifiedProductInWarehouseException(String userMessage, int httpStatus) {
        this("Product not specified in warehouse", userMessage, httpStatus, null);
    }
}
