package ru.yandex.practicum.service;

import jakarta.validation.ValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.yandex.practicum.api.StoreServiceApi;
import ru.yandex.practicum.dto.OrderDto;
import ru.yandex.practicum.dto.PaymentDto;
import ru.yandex.practicum.dto.ProductDto;
import ru.yandex.practicum.entity.Payment;
import ru.yandex.practicum.entity.PaymentStatus;
import ru.yandex.practicum.repository.PaymentRepository;

import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final StoreServiceApi storeServiceApi;
    private final PaymentRepository paymentRepository;

    /**
     * Рассчитывает только стоимость товаров в заказе.
     * Возвращает PaymentDto, где:
     * - totalPayment = сумма товаров
     * - остальные поля = null (доставка и налоги ещё не рассчитаны)
     */
    public PaymentDto calculateProductsTotal(OrderDto dto) {
        Map<UUID, Integer> products = dto.getProducts();

        if (products == null || products.isEmpty()) {
            return PaymentDto.builder()
                    .paymentId(null)
                    .totalPayment(0.0)
                    .deliveryTotal(null)
                    .feeTotal(null)
                    .build();
        }

        double total = 0.0;

        for (Map.Entry<UUID, Integer> entry : products.entrySet()) {
            UUID productId = entry.getKey();
            int quantity = entry.getValue();

            var response = storeServiceApi.getProductById(productId);

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new ValidationException("Не удалось получить цену товара: " + productId);
            }

            Double price = response.getBody().getPrice();
            if (price == null || price < 0) {
                throw new ValidationException("Некорректная цена товара: " + productId);
            }

            total += price * quantity;
        }

        log.debug("Рассчитана стоимость товаров: {}", total);

        return PaymentDto.builder()
                .totalPayment(total)
                .build();
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
        PaymentDto productsPayment = calculateProductsTotal(orderDto);
        double productsTotal = productsPayment.getTotalPayment();

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
        double totalAmount = calculateTotalCost(request);

        var paymentEntity = Payment.builder()
                .orderId(request.getOrderId())
                .shoppingCartId(request.getShoppingCartId())
                // Для доставки и налога нужно отдельно посчитать их значения, чтобы корректно сохранить
                .deliveryPrice(getDeliveryCostStub(request))
                .taxAmount(calculateProductsTotal(request).getTotalPayment() * 0.10)
                .totalAmount(totalAmount)
                .status(PaymentStatus.PENDING)
                .createdAt(java.time.LocalDateTime.now())
                .build();

        Payment saved = paymentRepository.save(paymentEntity);

        log.info("Создана оплата для заказа {} со статусом PENDING, итоговая сумма {}",
                request.getOrderId(), totalAmount);

        return buildPaymentDtoFromEntity(saved);
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

    /**
     * Заглушка для стоимости доставки.
     * В будущем тут будет вызов DeliveryService / Feign-клиента.
     */
    private double getDeliveryCostStub(OrderDto orderDto) {
        return 50.0; // фиксированная стоимость
    }
}
