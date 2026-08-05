package ru.yandex.practicum.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.yandex.practicum.dto.OrderDto;
import ru.yandex.practicum.dto.PaymentDto;
import ru.yandex.practicum.service.PaymentService;


@RestController
@RequestMapping("/api/v1/payment")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    /**
     * POST /api/v1/payment
     * Формирование оплаты для заказа (переход в платежный шлюз).
     */
    @PostMapping
    public ResponseEntity<PaymentDto> createPayment(@RequestBody OrderDto request) {
        PaymentDto response = paymentService.calculateProductsTotal(request);
        return ResponseEntity.ok(response);
    }
}
