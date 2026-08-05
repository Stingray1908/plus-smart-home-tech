package ru.yandex.practicum.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.yandex.practicum.dto.CreateNewOrderRequest;
import ru.yandex.practicum.dto.OrderDto;
import ru.yandex.practicum.dto.ProductReturnRequest;
import ru.yandex.practicum.service.OrderService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/order")
@RequiredArgsConstructor
@Slf4j
public class OrderController {

    private final OrderService orderService;

    @PutMapping
    public ResponseEntity<OrderDto> createNewOrder(@RequestBody CreateNewOrderRequest request) {
        log.debug("Получен запрос на создание заказа, shoppingCartId={}",
                request.getShoppingCart() != null ? request.getShoppingCart().getShoppingCartId() : null);

        OrderDto result = orderService.createOrder(request);
        return ResponseEntity.ok(result);
    }

    @GetMapping
    public ResponseEntity<List<OrderDto>> getOrders(
            @RequestParam(name = "username") String username,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        log.debug("Запрос заказов для username={}, page={}, size={}", username, page, size);
        List<OrderDto> orders = orderService.getOrdersByUsername(username, page, size);
        return ResponseEntity.ok(orders);
    }

    @PostMapping("/return")
    public ResponseEntity<OrderDto> returnOrder(@RequestBody ProductReturnRequest request) {
        OrderDto dto = orderService.returnOrder(request);
        return ResponseEntity.ok(dto);
    }

    @PostMapping("/payment")
    public ResponseEntity<OrderDto> payOrder(@RequestBody UUID orderId) {
        OrderDto dto = orderService.payOrder(orderId);
        return ResponseEntity.ok(dto);
    }

    @PostMapping("/payment/failed")
    public ResponseEntity<OrderDto> handlePaymentFailed(@RequestBody UUID orderId) {
        var dto = orderService.markPaymentFailed(orderId);
        return ResponseEntity.ok(dto);
    }

    @PostMapping("/delivery")
    public ResponseEntity<OrderDto> handleDelivery(@RequestBody UUID orderId) {
        var dto = orderService.markDelivered(orderId);
        return ResponseEntity.ok(dto);
    }

    @PostMapping("/delivery/failed")
    public ResponseEntity<OrderDto> handleDeliveryFailed(@RequestBody UUID orderId) {
        var dto = orderService.markDeliveryFailed(orderId);
        return ResponseEntity.ok(dto);
    }

    @PostMapping("/completed")
    public ResponseEntity<OrderDto> handleOrderCompleted(@RequestBody UUID orderId) {
        var dto = orderService.markCompleted(orderId);
        return ResponseEntity.ok(dto);
    }

    @PostMapping("/calculate/total")
    public ResponseEntity<OrderDto> handleCalculateTotal(@RequestBody UUID orderId) {
        var dto = orderService.calculateTotal(orderId);
        return ResponseEntity.ok(dto);
    }
}
