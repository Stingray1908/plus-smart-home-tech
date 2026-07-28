package ru.yandex.practicum.exception;


import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class CartNotActiveException extends RuntimeException {

        private final int httpStatus;
        private final String userMessage;

        public CartNotActiveException(String message, String userMessage, int httpStatus) {
            this(message, userMessage, httpStatus, null);
        }

        public CartNotActiveException(String message, String userMessage, int httpStatus, Throwable cause) {
            super(message, cause);
            this.userMessage = userMessage;
            this.httpStatus = httpStatus;
        }
}
