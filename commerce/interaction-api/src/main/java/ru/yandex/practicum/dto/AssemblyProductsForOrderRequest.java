package ru.yandex.practicum.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.UUID;

/**
 * Запрос на сбор заказа из товаров.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssemblyProductsForOrderRequest {



    /**
     * Идентификатор заказа в БД.
     */
    private UUID orderId;
    /**
     * Карта товаров: ключ — UUID товара, значение — количество (целое число).
     */
    private Map<UUID, Long> products;
}
