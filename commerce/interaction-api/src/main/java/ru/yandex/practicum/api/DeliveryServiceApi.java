package ru.yandex.practicum.api;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import ru.yandex.practicum.dto.DeliveryDto;
import ru.yandex.practicum.dto.OrderDto;

import java.util.UUID;

@FeignClient(name = "delivery", path = "/api/v1/delivery")
public interface DeliveryServiceApi {

    @PutMapping
    public ResponseEntity<DeliveryDto> createOrUpdateDelivery(@RequestBody DeliveryDto dto);

    @PostMapping("/cost")
    public ResponseEntity<Double> calculateDeliveryCost(@RequestBody OrderDto dto);

    @PostMapping("/successful")
    public ResponseEntity<DeliveryDto> markDeliverySuccessful(@RequestBody UUID orderId);

    @PostMapping("/picked")
    public ResponseEntity<DeliveryDto> markDeliveryPicked(@RequestBody UUID orderId);

    @PostMapping("/failed")
    public ResponseEntity<DeliveryDto> markDeliveryFailed(@RequestBody UUID orderId);


}
