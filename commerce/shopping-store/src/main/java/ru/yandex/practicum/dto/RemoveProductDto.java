package ru.yandex.practicum.dto;

import java.util.UUID;

public class RemoveProductDto {
    private UUID productId;

    public UUID getProductId() {
        return productId;
    }

    public void setProductId(UUID productId) {
        this.productId = productId;
    }
}
