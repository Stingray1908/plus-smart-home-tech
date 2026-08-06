package ru.yandex.practicum.api;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import ru.yandex.practicum.dto.OrderDto;
import ru.yandex.practicum.dto.PaymentDto;
import ru.yandex.practicum.dto.ShoppingCartDto;

import java.util.UUID;

@FeignClient(name = "payment", path = "/api/v1/payment")
public interface PaymentServiceApi {

    /**
     * POST /api/v1/payment
     * Формирование оплаты для заказа (переход в платежный шлюз).
     */
    @PostMapping
    public ResponseEntity<PaymentDto> createPayment(@RequestBody OrderDto request);

    /**
     * POST /api/v1/payment/totalCost
     * Расчёт полной стоимости заказа: товары + НДС (10%) + доставка.
     */
    @PostMapping("/totalCost")
    public ResponseEntity<Double> calculateTotalCost(@RequestBody OrderDto orderDto);

    /**
     * POST /api/v1/payment/productCost
     * Только расчёт стоимости товаров (для UI).
     */
    @PostMapping("/productCost")
    public ResponseEntity<PaymentDto> calculateProductCost(@RequestBody OrderDto orderDto);

    /**
     * POST /api/v1/payment/refund
     * Эмуляция успешной оплаты от платёжного шлюза.
     * Логика: найти платёж -> поставить статус SUCCESS -> вызвать сервис заказов.
     */
    @PostMapping("/refund")
    public ResponseEntity<PaymentDto> simulateSuccessPayment(@RequestBody UUID paymentId);

    /**
     * POST /api/v1/payment/failed
     * Эмуляция отказа в оплате от платёжного шлюза.
     * Логика: найти платёж -> поставить статус FAILED -> вызвать сервис заказов.
     */
    @PostMapping("/failed")
    public ResponseEntity<PaymentDto> simulateFailedPayment(@RequestBody UUID paymentId);
}
