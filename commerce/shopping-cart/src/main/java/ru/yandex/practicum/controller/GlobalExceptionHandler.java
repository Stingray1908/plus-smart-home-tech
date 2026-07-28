package ru.yandex.practicum.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import ru.yandex.practicum.common.ErrorResponse;
import ru.yandex.practicum.exception.CartNotActiveException;
import ru.yandex.practicum.exception.CartNotFoundException;
import ru.yandex.practicum.exception.NoProductsInShoppingCartException;
import ru.yandex.practicum.exception.NotAuthorizedUserException;

import java.time.Instant;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(NotAuthorizedUserException.class)
    public ResponseEntity<ErrorResponse> handleNotAuthorizedUserException(
            NotAuthorizedUserException ex,
            HttpServletRequest request
    ) {
        ErrorResponse response = ErrorResponse.builder()
                .httpStatus(ex.getHttpStatus())
                .userMessage(ex.getUserMessage())
                .message(ex.getMessage())
                .timestamp(Instant.now())
                .build();

        return ResponseEntity.status(ex.getHttpStatus()).body(response);
    }

    @ExceptionHandler(NoProductsInShoppingCartException.class)
    public ResponseEntity<ErrorResponse> handleNoProductsInShoppingCartException(
            NoProductsInShoppingCartException ex,
            HttpServletRequest request
    ) {
        ErrorResponse response = ErrorResponse.builder()
                .httpStatus(ex.getHttpStatus())
                .userMessage(ex.getUserMessage())
                .message(ex.getMessage())
                .timestamp(Instant.now())
                .build();

        return ResponseEntity.status(ex.getHttpStatus()).body(response);
    }

    @ExceptionHandler(CartNotActiveException.class)
    public ResponseEntity<ErrorResponse> handleCartNotActive(CartNotActiveException ex) {
        ErrorResponse response = ErrorResponse.builder()
                .httpStatus(ex.getHttpStatus())
                .userMessage(ex.getUserMessage())
                .message(ex.getMessage())
                .timestamp(Instant.now())
                .build();

        return ResponseEntity.status(ex.getHttpStatus()).body(response);
    }

    @ExceptionHandler(CartNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleCartNotFoundException(CartNotFoundException ex) {
        ErrorResponse response = ErrorResponse.builder()
                .httpStatus(ex.getHttpStatus())
                .userMessage(ex.getUserMessage())
                .message(ex.getMessage())
                .timestamp(Instant.now())
                .build();

        return ResponseEntity.status(ex.getHttpStatus()).body(response);
    }
}
