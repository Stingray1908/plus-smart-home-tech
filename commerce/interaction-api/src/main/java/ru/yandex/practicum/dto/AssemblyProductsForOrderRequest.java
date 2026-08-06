package ru.yandex.practicum.dto;

import lombok.Builder;
import lombok.Data;
import java.util.Map;
import java.util.UUID;

/**
 * Запрос на сбор заказа из товаров.
 */
@Data
@Builder
public class AssemblyProductsForOrderRequest {

    /**
     * Карта товаров: ключ — UUID товара, значение — количество (целое число).
     */
    private Map<UUID, Long> products;

    /**
     * Идентификатор заказа в БД.
     */
    private UUID orderId;
}
