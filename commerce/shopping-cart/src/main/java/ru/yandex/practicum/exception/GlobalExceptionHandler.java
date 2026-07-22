package ru.yandex.practicum.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.time.Instant;

@ControllerAdvice
public class GlobalExceptionHandler {

  /*  @ExceptionHandler(NotAuthorizedUserException.class)
    public ResponseEntity<ErrorResponse> handleNotAuthorizedUserException(
            NotAuthorizedUserException ex,
            HttpServletRequest request
    ) {

        ErrorResponse response = ErrorResponse.builder()
                .cause(ex.getCause())
                .stackTrace(ex.getStackTrace())
                .httpStatus(ex.getHttpStatus())
                .userMessage(ex.getUserMessage())
                .message(ex.getMessage())
                .localizedMessage(ex.getLocalizedMessage())
                .suppressed(java.util.List.of(ex.getSuppressed()))
                .timestamp(java.time.Instant.now())
                .build();

        return ResponseEntity.status(ex.getHttpStatus()).body(response);
    }
*/
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

   /* @ExceptionHandler(NotAuthorizedUserException.class)
    public ResponseEntity<ErrorResponse> handleNotAuthorized(NotAuthorizedUserException ex) {
        ErrorResponse response = new ErrorResponse(ex.getUserMessage(), ex.getHttpStatus());
        return ResponseEntity.status(ex.getHttpStatus()).body(response);
    }*/

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
}
