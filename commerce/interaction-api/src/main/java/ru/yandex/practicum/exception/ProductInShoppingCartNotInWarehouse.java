package ru.yandex.practicum.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class ProductInShoppingCartNotInWarehouse extends RuntimeException {

    private final int httpStatus;
    private final String userMessage;

    public ProductInShoppingCartNotInWarehouse(String message, String userMessage, int httpStatus, Throwable cause) {
        super(message, cause);
        this.userMessage = userMessage;
        this.httpStatus = httpStatus;
    }

    public ProductInShoppingCartNotInWarehouse(String userMessage, int httpStatus) {
        this("ProductInShoppingCartNotInWarehouse", userMessage, httpStatus, null);
    }
}
