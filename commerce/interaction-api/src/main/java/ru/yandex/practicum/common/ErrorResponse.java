package ru.yandex.practicum.common;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ErrorResponse {
    private int httpStatus;
    private String userMessage;
    private String message;
    private java.time.Instant timestamp;
}

