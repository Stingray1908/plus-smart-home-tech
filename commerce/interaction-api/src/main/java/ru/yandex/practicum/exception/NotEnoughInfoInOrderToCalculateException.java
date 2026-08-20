package ru.yandex.practicum.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class NotEnoughInfoInOrderToCalculateException extends RuntimeException {

    private final int httpStatus;
    private final String userMessage;

    public NotEnoughInfoInOrderToCalculateException(String message, String userMessage, int httpStatus, Throwable cause) {
        super(message, cause);
        this.userMessage = userMessage;
        this.httpStatus = httpStatus;
    }

    public NotEnoughInfoInOrderToCalculateException(String userMessage, int httpStatus) {
        this("Not Enough Info In Order To Calculate", userMessage, httpStatus, null);
    }
}
