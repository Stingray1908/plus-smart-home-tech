package ru.yandex.practicum.dto;

import lombok.Builder;
import lombok.Getter;
import java.util.Map;
import java.util.UUID;

@Getter
@Builder
public class ShoppingCartDto {

    private final UUID shoppingCartId;
    private final Map<UUID, Long> products;
}
