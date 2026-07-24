package ru.yandex.practicum.cart.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class NoProductsInShoppingCartException extends RuntimeException {

    private final int httpStatus;
    private final String userMessage;

    public NoProductsInShoppingCartException(String message, String userMessage, int httpStatus) {
        this(message, userMessage, httpStatus, null);
    }

    public NoProductsInShoppingCartException(String message, String userMessage, int httpStatus, Throwable cause) {
        super(message, cause);
        this.userMessage = userMessage;
        this.httpStatus = httpStatus;
    }
}

