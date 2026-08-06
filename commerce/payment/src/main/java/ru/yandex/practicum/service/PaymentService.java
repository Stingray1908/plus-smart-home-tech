package ru.yandex.practicum.service;

import jakarta.validation.ValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.yandex.practicum.api.OrderServiceApi;
import ru.yandex.practicum.api.StoreServiceApi;
import ru.yandex.practicum.dto.OrderDto;
import ru.yandex.practicum.dto.PaymentDto;
import ru.yandex.practicum.dto.ProductDto;
import ru.yandex.practicum.entity.Payment;
import ru.yandex.practicum.entity.PaymentStatus;
import ru.yandex.practicum.exception.PaymentNotFoundException;
import ru.yandex.practicum.repository.PaymentRepository;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final StoreServiceApi storeServiceApi;
    private final PaymentRepository paymentRepository;
    private final OrderServiceApi orderServiceApi;

    /**
     * Рассчитывает только стоимость товаров в заказе.
     * Возвращает PaymentDto, где:
     * - totalPayment = сумма товаров
     * - остальные поля = null (доставка и налоги ещё не рассчитаны)
     */
    private double calculateProductsTotal(OrderDto dto) {
        if (dto.getProducts() == null || dto.getProducts().isEmpty()) {
            return 0.0;
        }

        double total = 0.0;
        for (var entry : dto.getProducts().entrySet()) {
            UUID productId = entry.getKey();
            Long quantity = entry.getValue();

            // ✅ Реальный вызов к сервису shopping-store через Feign
            var response = storeServiceApi.getProductById(productId);
            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new IllegalStateException("Не удалось получить продукт id=" + productId);
            }
            ProductDto product = response.getBody();
            if (product == null || product.getPrice() == null) {
                throw new IllegalStateException("Продукт id=" + productId + " не содержит цены");
            }

            total += product.getPrice().doubleValue() * quantity;
        }
        return total;
    }

    /**
     * Расчёт полной стоимости заказа.
     * Возвращает только итоговую сумму (Double), как требует ТЗ.
     *
     * Формула:
     * - сумма товаров
     * - НДС 10% от суммы товаров
     * - стоимость доставки (заглушка)
     */
    public Double calculateTotalCost(OrderDto orderDto) {
        // 1. Считаем стоимость товаров
        Double productsTotal = calculateProductsTotal(orderDto);

        // 2. НДС 10% от стоимости товаров
        double vat = productsTotal * 0.10;

        // 3. Стоимость доставки — заглушка
        double deliveryTotal = getDeliveryCostStub(orderDto);

        // 4. Итоговая сумма
        double finalTotal = productsTotal + vat + deliveryTotal;

        log.debug(
                "Расчёт полной стоимости: товары={}, НДС={}, доставка={}, итого={}",
                productsTotal, vat, deliveryTotal, finalTotal
        );

        return finalTotal;
    }

    /**
     * Создание оплаты:
     * - расчёт полной стоимости
     * - сохранение в БД со статусом PENDING
     * - формирование и возврат PaymentDto
     */
    public PaymentDto createPayment(OrderDto request) {
        double productsTotal = calculateProductsTotal(request);
        double vat = productsTotal * 0.10;
        double deliveryTotal = request.getDeliveryPrice() != null
                ? request.getDeliveryPrice().doubleValue()
                : 0.0;

        double finalTotal = productsTotal + vat + deliveryTotal;

        Payment payment = Payment.builder()
                .orderId(request.getOrderId())
                .shoppingCartId(request.getShoppingCartId())
                .productTotal(productsTotal)          // ✅ теперь имя совпадает с ТЗ
                .deliveryPrice(deliveryTotal)
                .taxAmount(vat)
                .totalAmount(finalTotal)
                .status(PaymentStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build();

        payment = paymentRepository.save(payment);

        return PaymentDto.builder()
                .paymentId(payment.getId())
                .totalPayment(payment.getTotalAmount())
                .deliveryTotal(payment.getDeliveryPrice())
                .feeTotal(payment.getTaxAmount())
                .build();
    }


    /**
     * Формирует PaymentDto из сущности Payment.
     * Вынесено в отдельный метод, чтобы отделить логику маппинга от бизнес-логики.
     */
    private PaymentDto buildPaymentDtoFromEntity(Payment payment) {
        return PaymentDto.builder()
                .paymentId(payment.getId())
                .totalPayment(payment.getTotalAmount())
                .deliveryTotal(payment.getDeliveryPrice())
                .feeTotal(payment.getTaxAmount())
                .build();
    }

    public PaymentDto markPaymentSuccess(UUID paymentId) {
        Optional<Payment> optionalPayment = paymentRepository.findById(paymentId);
        if (optionalPayment.isEmpty()) {
            throw new PaymentNotFoundException("Платёж не найден: " + paymentId,
                    404);
        }

        Payment payment = optionalPayment.get();
        if (payment.getStatus() != PaymentStatus.PENDING) {
            throw new ValidationException(
                    "Нельзя установить SUCCESS для платежа со статусом: " + payment.getStatus());
        }

        payment.setStatus(PaymentStatus.SUCCESS);
        payment.setUpdatedAt(java.time.LocalDateTime.now());
        Payment saved = paymentRepository.save(payment);

        // Вызываем существующий метод из твоего Feign-клиента
        try {
            orderServiceApi.payOrder(saved.getOrderId());
            log.info("Сервис заказов подтвердил оплату для заказа {}", saved.getOrderId());
        } catch (Exception e) {
            log.error("Не удалось уведомить сервис заказов об оплате заказа {}", saved.getOrderId(), e);
            // Оставляем платёж в SUCCESS — это частый паттерн в учебных задачах
        }

        return buildPaymentDtoFromEntity(saved);
    }

    public PaymentDto markPaymentFailed(UUID paymentId) {
        Optional<Payment> optionalPayment = paymentRepository.findById(paymentId);
        if (optionalPayment.isEmpty()) {
            throw new PaymentNotFoundException("Платёж не найден: " + paymentId,
                    404);
        }

        Payment payment = optionalPayment.get();
        if (payment.getStatus() != PaymentStatus.PENDING) {
            throw new ValidationException(
                    "Нельзя установить FAILED для платежа со статусом: " + payment.getStatus());
        }

        payment.setStatus(PaymentStatus.FAILED);
        payment.setUpdatedAt(java.time.LocalDateTime.now());
        Payment saved = paymentRepository.save(payment);

        // Используем существующий метод handlePaymentFailed из твоего Feign
        try {
            orderServiceApi.handlePaymentFailed(saved.getOrderId());
            log.info("Сервис заказов уведомлён о неудачной оплате заказа {}", saved.getOrderId());
        } catch (Exception e) {
            log.error("Не удалось уведомить сервис заказов о неудачной оплате заказа {}", saved.getOrderId(), e);
        }

        return buildPaymentDtoFromEntity(saved);
    }

    /**
     * Заглушка для стоимости доставки.
     * В будущем тут будет вызов DeliveryService / Feign-клиента.
     */
    private double getDeliveryCostStub(OrderDto orderDto) {
        return 50.0; // фиксированная стоимость
    }
}
