package ru.yandex.practicum.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class NoDeliveryFoundException extends RuntimeException {

    private final int httpStatus;
    private final String userMessage;

    public NoDeliveryFoundException(String message, String userMessage, int httpStatus, Throwable cause) {
        super(message, cause);
        this.userMessage = userMessage;
        this.httpStatus = httpStatus;
    }

    public NoDeliveryFoundException(String userMessage, int httpStatus) {
        this("PaymentNotFoundException", userMessage, httpStatus, null);
    }
}
