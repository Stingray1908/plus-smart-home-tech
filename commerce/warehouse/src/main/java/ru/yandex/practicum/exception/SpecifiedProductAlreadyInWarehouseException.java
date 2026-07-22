package ru.yandex.practicum.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class SpecifiedProductAlreadyInWarehouseException extends RuntimeException {

    private final int httpStatus;
    private final String userMessage;

    public SpecifiedProductAlreadyInWarehouseException(
            String message,
            String userMessage,
            int httpStatus,
            Throwable cause
    ) {
        super(message, cause);
        this.userMessage = userMessage;
        this.httpStatus = httpStatus;
    }

    public SpecifiedProductAlreadyInWarehouseException(String userMessage, int httpStatus) {
        this("Product is already specified in warehouse", userMessage, httpStatus, null);
    }
}
