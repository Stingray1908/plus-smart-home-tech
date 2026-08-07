package ru.yandex.practicum.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.yandex.practicum.dto.OrderDto;
import ru.yandex.practicum.dto.PaymentDto;
import ru.yandex.practicum.service.PaymentService;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payment")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    /**
     * POST /api/v1/payment
     * Формирование оплаты для заказа (переход в платежный шлюз).
     */

    //
    @PostMapping
    public ResponseEntity<PaymentDto> createPayment(@RequestBody OrderDto request) {
        PaymentDto response = paymentService.createPayment(request);
        return ResponseEntity.ok(response);
    }

    /**
     * POST /api/v1/payment/totalCost
     * Расчёт полной стоимости заказа: товары + НДС (10%) + доставка.
     */

    //
    @PostMapping("/totalCost")
    public ResponseEntity<Double> calculateTotalCost(@RequestBody OrderDto orderDto) {
        Double totalCost = paymentService.calculateTotalCost(orderDto);
        return ResponseEntity.ok(totalCost);
    }

    /**
     * POST /api/v1/payment/productCost
     * Только расчёт стоимости товаров (для UI).
     */

    //
    @PostMapping("/productCost")
    public ResponseEntity<Double> calculateProductCost(@RequestBody OrderDto orderDto) {
        Double dto = paymentService.calculateProducts(orderDto);
        return ResponseEntity.ok(dto);
    }

    /**
     * POST /api/v1/payment/refund
     * Эмуляция успешной оплаты от платёжного шлюза.
     * Логика: найти платёж -> поставить статус SUCCESS -> вызвать сервис заказов.
     */

//
    @PostMapping("/refund")
    public ResponseEntity<PaymentDto> simulateSuccessPayment(@RequestBody UUID paymentId) {
        PaymentDto dto = paymentService.markPaymentSuccess(paymentId);
        return ResponseEntity.ok(dto);
    }

    /**
     * POST /api/v1/payment/failed
     * Эмуляция отказа в оплате от платёжного шлюза.
     * Логика: найти платёж -> поставить статус FAILED -> вызвать сервис заказов.
     */

    //
    @PostMapping("/failed")
    public ResponseEntity<PaymentDto> simulateFailedPayment(@RequestBody UUID paymentId) {
        PaymentDto dto = paymentService.markPaymentFailed(paymentId);
        return ResponseEntity.ok(dto);
    }
}


