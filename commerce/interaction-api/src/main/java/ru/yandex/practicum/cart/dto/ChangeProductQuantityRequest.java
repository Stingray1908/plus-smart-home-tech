package ru.yandex.practicum.cart.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ChangeProductQuantityRequest {

    private UUID productId;
    private long newQuantity;
}
