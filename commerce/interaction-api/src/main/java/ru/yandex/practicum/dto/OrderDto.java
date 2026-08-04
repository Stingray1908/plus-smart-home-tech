package ru.yandex.practicum.dto;

import lombok.Builder;
import lombok.Data;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
public class OrderDto {
    private UUID orderId;
    private UUID shoppingCartId;
    private Map<UUID, Integer> products;
    private UUID paymentId;
    private UUID deliveryId;
    private OrderStatus state;
    private Double deliveryWeight;
    private Double deliveryVolume;
    private Boolean fragile;
    private Long totalPrice;
    private Long deliveryPrice;
    private Long productPrice;
}
