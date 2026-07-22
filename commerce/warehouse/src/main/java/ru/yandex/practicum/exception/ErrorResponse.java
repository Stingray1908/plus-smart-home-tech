package ru.yandex.practicum.exception;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

/*
@Data
@Builder
public class ErrorResponse {
    private Throwable cause;
    private StackTraceElement[] stackTrace;
    private int httpStatus;
    private String userMessage;
    private String message;
    private List<Throwable> suppressed;
    private String localizedMessage;
    private Instant timestamp;
}
*/
@Data
@Builder
public class ErrorResponse {
    private int httpStatus;
    private String userMessage;      // <-- для человека на экране
    private String message;         // <-- для логов разработчика
    private Instant timestamp;
}

