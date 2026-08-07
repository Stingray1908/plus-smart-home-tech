package ru.yandex.practicum.service;

import feign.FeignException;
import jakarta.validation.ValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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

    // так то вроде ровно
    public double calculateProducts(OrderDto dto) {
        if (dto.getProducts() == null || dto.getProducts().isEmpty()) {
            return 0.0;
        }

        double total = 0.0;
        for (var entry : dto.getProducts().entrySet()) {
            UUID productId = entry.getKey();
            Long quantity = entry.getValue();

            var response = storeServiceApi.getProductById(productId);
            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new IllegalStateException("Не удалось получить продукт id=" + productId);
            }
            ProductDto product = response.getBody();
            if (product == null || product.getPrice() == null) {
                throw new IllegalStateException("Продукт id=" + productId + " не содержит цены");
            }

            total += product.getPrice() * quantity;
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
        Double productsTotal = calculateProducts(orderDto);

        // Если товаров нет — возвращаем 0
        if (productsTotal == null || productsTotal <= 0.0) {
            productsTotal = 0.0;
        }

        // 2. НДС 10% от стоимости товаров
        double vat = productsTotal * 0.10;

        // 3. Стоимость доставки — берём из DTO (она уже посчитана сервисом доставки)
        double deliveryTotal = (orderDto.getDeliveryPrice() != null) ? orderDto.getDeliveryPrice().doubleValue() : 0.0;

        // 4. Итоговая сумма: товары + НДС + доставка
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
     //
    @Transactional
    public PaymentDto createPayment(OrderDto orderDto) {
        // 1. Считаем стоимость товаров (независимо, чтобы гарантировать корректность расчёта)
        Double productTotal = calculateProducts(orderDto);
        if (productTotal == null) {
            productTotal = 0.0;
        }

        // 2. Стоимость доставки из DTO
        Double deliveryPrice = orderDto.getDeliveryPrice().doubleValue();

        // 3. Рассчитываем НДС (10%) строго по алгоритму из ТЗ
        Double taxAmount = productTotal * 0.10;

        // 4. Считаем итоговую сумму
        Double totalAmount = orderDto.getTotalPrice().doubleValue();

        log.info("Создание платежа для заказа {}. Товары: {}, Доставка: {}, Налог: {}, Итого: {}",
                orderDto.getOrderId(), productTotal, deliveryPrice, taxAmount, totalAmount);

        Payment payment = Payment.builder()
                .orderId(orderDto.getOrderId())
                .shoppingCartId(orderDto.getShoppingCartId())
                // Сохраняем все компоненты стоимости, как требует ТЗ
                .productTotal(productTotal)
                .deliveryPrice(deliveryPrice)
                .taxAmount(taxAmount)
                .totalAmount(totalAmount)
                // Статус по ТЗ: изначально PENDING
                .status(PaymentStatus.PENDING)
                .build();

        paymentRepository.save(payment);
        return PaymentDto.builder()
                .paymentId(payment.getId())
                .totalPayment(payment.getTotalAmount())      // <-- totalPayment = totalAmount
                .deliveryTotal(payment.getDeliveryPrice())    // <-- deliveryTotal = deliveryPrice
                .feeTotal(payment.getTaxAmount())             // <-- feeTotal = taxAmount (НДС)
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

    @Transactional
    public PaymentDto markPaymentSuccess(UUID paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new IllegalArgumentException("Платёж не найден"));

        // а. Проверить, что идентификатор оплаты существует (сделано выше)

        // b. Изменить статус на SUCCESS
        payment.setStatus(PaymentStatus.SUCCESS);
        paymentRepository.save(payment);

        // с. Вызвать изменение в сервисе заказов
        // Мы передаём orderId, который лежит внутри платежа
        try {
            orderServiceApi.markOrderPaymentAsPaid(payment.getOrderId());
            log.info("Статус заказа успешно обновлён в order-service для orderId={}", payment.getOrderId());
        } catch (FeignException e) {
            // Критическая ошибка: платёж успешен, а заказ не обновился.
            // Тут нужна стратегия: либо откатить платёж, либо алерт, либо retry.
            log.error("Не удалось уведомить сервис заказов об успешной оплате. Платёж: {}, Ошибка: {}", paymentId, e.getMessage());
            throw new IllegalStateException("Платёж успешен, но сервис заказов недоступен", e);
        }

        return buildPaymentDtoFromEntity(payment);
    }

    @Transactional
    public PaymentDto markPaymentFailed(UUID paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new IllegalArgumentException("Платёж не найден"));

        payment.setStatus(PaymentStatus.FAILED);
        paymentRepository.save(payment);

        try {
            orderServiceApi.markOrderPaymentAsFailed(payment.getOrderId());
            log.info("Статус заказа обновлен на FAILED в order-service для orderId={}", payment.getOrderId());
        } catch (FeignException e) {
            log.error("Не удалось уведомить сервис заказов о неудачной оплате. Платёж: {}, Ошибка: {}", paymentId, e.getMessage());
            throw new IllegalStateException("Платёж провален, но сервис заказов недоступен", e);
        }

        return buildPaymentDtoFromEntity(payment);
    }
}
