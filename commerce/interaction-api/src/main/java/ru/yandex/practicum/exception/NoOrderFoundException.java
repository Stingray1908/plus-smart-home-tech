package ru.yandex.practicum.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class NoOrderFoundException extends RuntimeException {

    private final int httpStatus;
    private final String userMessage;

    public NoOrderFoundException(String message, String userMessage, int httpStatus, Throwable cause) {
        super(message, cause);
        this.userMessage = userMessage;
        this.httpStatus = httpStatus;
    }

    public NoOrderFoundException(String userMessage, int httpStatus) {
        this("Order not found", userMessage, httpStatus, null);
    }
}

