package ru.yandex.practicum;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NewProductInWarehouseRequest {
    private UUID productId;
    private boolean fragile;
    private DimensionDto dimension;
    private double weight;
}
