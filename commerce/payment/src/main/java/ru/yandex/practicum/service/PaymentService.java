package ru.yandex.practicum.service;

import feign.FeignException;
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
import ru.yandex.practicum.repository.PaymentRepository;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.function.BiFunction;

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
    public BigDecimal calculateProducts(OrderDto dto) {
        if (dto.getProducts() == null || dto.getProducts().isEmpty()) {
            return BigDecimal.ZERO;
        }

        BigDecimal total = BigDecimal.ZERO;
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

            total = total.add(product.getPrice().add(new BigDecimal(quantity)));
        }
        return total;
    }

    /**
     * Расчёт полной стоимости заказа.
     * Возвращает только итоговую сумму (Double), как требует ТЗ.
     * <p>
     * Формула:
     * - сумма товаров
     * - НДС 10% от суммы товаров
     * - стоимость доставки (заглушка)
     */
    public BigDecimal calculateTotalCost(OrderDto orderDto) {
        BigDecimal productsTotal = calculateProducts(orderDto);

        if (productsTotal == null || productsTotal.compareTo(BigDecimal.ZERO) <= 0) {
            productsTotal = BigDecimal.ZERO;
        }

        BigDecimal vat = productsTotal.multiply(new BigDecimal("0.10"));
        BigDecimal deliveryTotal = (orderDto.getDeliveryPrice() != null)
                ? orderDto.getDeliveryPrice()
                : BigDecimal.ZERO;

        BigDecimal finalTotal = productsTotal.add(vat).add(deliveryTotal);

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
    @Transactional
    public PaymentDto createPayment(OrderDto orderDto) {
        BigDecimal productTotal = calculateProducts(orderDto);
        if (productTotal == null) {
            productTotal = BigDecimal.ZERO;
        }

        BigDecimal deliveryPrice = orderDto.getDeliveryPrice();
        BigDecimal taxAmount = productTotal.multiply(new BigDecimal("0.10"));
        BigDecimal totalAmount = orderDto.getTotalPrice();

        log.info("Создание платежа для заказа {}. Товары: {}, Доставка: {}, Налог: {}, Итого: {}",
                orderDto.getOrderId(), productTotal, deliveryPrice, taxAmount, totalAmount);

        Payment payment = Payment.builder()
                .orderId(orderDto.getOrderId())
                .shoppingCartId(orderDto.getShoppingCartId())
                .productTotal(productTotal)
                .deliveryPrice(deliveryPrice)
                .taxAmount(taxAmount)
                .totalAmount(totalAmount)
                .status(PaymentStatus.PENDING)
                .build();

        paymentRepository.save(payment);
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

    @Transactional
    public PaymentDto markPaymentSuccess(UUID paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new IllegalArgumentException("Платёж не найден"));

        payment.setStatus(PaymentStatus.SUCCESS);
        paymentRepository.save(payment);

        try {
            orderServiceApi.markOrderPaymentAsPaid(payment.getOrderId());
            log.info("Статус заказа успешно обновлён в order-service для orderId={}", payment.getOrderId());
        } catch (FeignException e) {
            log.error("Не удалось уведомить сервис заказов об успешной оплате. Платёж: {}, Ошибка: {}",
                    paymentId, e.getMessage());
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
            log.error("Не удалось уведомить сервис заказов о неудачной оплате. Платёж: {}, Ошибка: {}",
                    paymentId, e.getMessage());
            throw new IllegalStateException("Платёж провален, но сервис заказов недоступен", e);
        }

        return buildPaymentDtoFromEntity(payment);
    }
}
