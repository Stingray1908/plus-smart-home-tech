package ru.yandex.practicum.dto;

import lombok.Builder;
import lombok.Getter;
import java.util.UUID;

@Getter
@Builder
public class ChangeProductQuantityRequest {

    private final UUID productId;
    private final long newQuantity;
}
