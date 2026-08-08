package ru.yandex.practicum.api;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import ru.yandex.practicum.dto.CreateNewOrderRequest;
import ru.yandex.practicum.dto.OrderDto;
import ru.yandex.practicum.dto.ProductReturnRequest;

import java.util.UUID;

@FeignClient(name = "order", path = "/api/v1/order")
public interface OrderServiceApi {

    @PutMapping
    public ResponseEntity<OrderDto> createNewOrder(@RequestBody CreateNewOrderRequest request);

    @PostMapping("/return")
    public ResponseEntity<OrderDto> returnOrder(@RequestBody ProductReturnRequest request);

    @PostMapping("/payment")
    public ResponseEntity<OrderDto> markOrderPaymentAsPaid(@RequestBody UUID orderId);

    @PostMapping("/payment/failed")
    public ResponseEntity<OrderDto> markOrderPaymentAsFailed(@RequestBody UUID orderId);

    @PostMapping("/delivery")
    public ResponseEntity<OrderDto> handleDelivery(@RequestBody UUID orderId);

    @PostMapping("/delivery/failed")
    public ResponseEntity<OrderDto> handleDeliveryFailed(@RequestBody UUID orderId);

    @PostMapping("/completed")
    public ResponseEntity<OrderDto> handleOrderCompleted(@RequestBody UUID orderId);

    @PostMapping("/calculate/total")
    public ResponseEntity<OrderDto> handleCalculateTotal(@RequestBody UUID orderId);

    @PostMapping("/calculate/delivery")
    public ResponseEntity<OrderDto> handleCalculateDelivery(@RequestBody UUID orderId);

    @PostMapping("/assembly")
    public ResponseEntity<OrderDto> handleAssembly(@RequestBody UUID orderId);

    @PostMapping("/assembly/failed")
    public ResponseEntity<OrderDto> handleAssemblyFailed(@RequestBody UUID orderId);
}
