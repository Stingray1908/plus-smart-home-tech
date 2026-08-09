package ru.yandex.practicum.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.yandex.practicum.api.DeliveryServiceApi;
import ru.yandex.practicum.dto.DeliveryDto;
import ru.yandex.practicum.dto.OrderDto;
import ru.yandex.practicum.service.DeliveryService;

import java.math.BigDecimal;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/delivery")
@RequiredArgsConstructor
public class DeliveryController implements DeliveryServiceApi {

    private final DeliveryService deliveryService;

    /**
     * PUT /api/v1/delivery
     * Создать новую доставку.
     * Request body: DeliveryDto (с deliveryId, orderId, адресами, deliveryState)
     * Response: DeliveryDto
     */
    @Override
    public ResponseEntity<DeliveryDto> createOrUpdateDelivery(@RequestBody DeliveryDto dto) {
        DeliveryDto saved = deliveryService.saveDelivery(dto);
        return ResponseEntity.ok(saved);
    }

    /**
     * POST /api/v1/delivery/successful
     * Эмуляция успешной доставки.
     * Request body: UUID orderId (JSON).
     * Если доставка не найдена — выбрасывается исключение → Spring отдаст 404 со стеком.
     */
    @Override
    public ResponseEntity<DeliveryDto> markDeliverySuccessful(@RequestBody UUID orderId) {
        DeliveryDto dto = deliveryService.markDeliverySuccessful(orderId);
        return ResponseEntity.ok(dto);
    }

    @Override
    public ResponseEntity<DeliveryDto> markDeliveryPicked(@RequestBody UUID orderId) {
        DeliveryDto dto = deliveryService.markDeliveryPicked(orderId);
        return ResponseEntity.ok(dto);
    }

    @Override
    public ResponseEntity<DeliveryDto> markDeliveryFailed(@RequestBody UUID orderId) {
        DeliveryDto dto = deliveryService.markDeliveryFailed(orderId);
        return ResponseEntity.ok(dto);
    }

    @Override
    public ResponseEntity<BigDecimal> calculateDeliveryCost(@RequestBody OrderDto dto) {
        BigDecimal cost = deliveryService.calculateCost(dto);
        return ResponseEntity.ok(cost);
    }
}
