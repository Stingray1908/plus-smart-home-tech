package ru.yandex.practicum.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.yandex.practicum.dto.DeliveryDto;
import ru.yandex.practicum.service.DeliveryService;

@RestController
@RequestMapping("/api/v1/delivery")
@RequiredArgsConstructor
public class DeliveryController {

    private final DeliveryService deliveryService;

    /**
     * PUT /api/v1/delivery
     * Создать новую доставку.
     * Request body: DeliveryDto (с deliveryId, orderId, адресами, deliveryState)
     * Response: DeliveryDto
     */
    @PutMapping
    public ResponseEntity<DeliveryDto> createOrUpdateDelivery(@RequestBody DeliveryDto dto) {
        DeliveryDto saved = deliveryService.saveDelivery(dto);
        return ResponseEntity.ok(saved);
    }
}
