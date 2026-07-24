package ru.yandex.practicum;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShoppingCartDto {
    private UUID shoppingCartId;
    private Map<UUID, Long> products;
}
