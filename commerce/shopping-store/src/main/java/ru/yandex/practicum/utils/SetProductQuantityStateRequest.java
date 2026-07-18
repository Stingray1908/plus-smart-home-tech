package ru.yandex.practicum.utils;

import lombok.Builder;
import lombok.Data;
import ru.yandex.practicum.enums.QuantityState;

import java.util.UUID;

@Builder
@Data
public class SetProductQuantityStateRequest {
    private UUID productId;
    private QuantityState quantityState;
}

