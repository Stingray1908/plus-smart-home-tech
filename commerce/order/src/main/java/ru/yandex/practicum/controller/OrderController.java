package ru.yandex.practicum.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.yandex.practicum.dto.CreateNewOrderRequest;
import ru.yandex.practicum.dto.OrderDto;
import ru.yandex.practicum.service.OrderService;

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
}
