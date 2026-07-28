package ru.yandex.practicum.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class ProductInShoppingCartLowQuantityInWarehouse extends RuntimeException {

    private final int httpStatus;
    private final String userMessage;

    public ProductInShoppingCartLowQuantityInWarehouse(
            String message,
            String userMessage,
            int httpStatus,
            Throwable cause
    ) {
        super(message, cause);
        this.userMessage = userMessage;
        this.httpStatus = httpStatus;
    }

    public ProductInShoppingCartLowQuantityInWarehouse(String userMessage, int httpStatus) {
        this("Low quantity of product in warehouse for shopping cart", userMessage, httpStatus, null);
    }
}
