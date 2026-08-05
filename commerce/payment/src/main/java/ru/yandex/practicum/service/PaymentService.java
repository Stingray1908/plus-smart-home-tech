package ru.yandex.practicum.service;

import jakarta.validation.ValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.yandex.practicum.api.StoreServiceApi;
import ru.yandex.practicum.dto.OrderDto;
import ru.yandex.practicum.dto.PaymentDto;
import ru.yandex.practicum.dto.ProductDto;

import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final StoreServiceApi storeServiceApi;

    /**
     * Рассчитывает только стоимость товаров в заказе.
     * Возвращает PaymentDto, где:
     * - totalPayment = сумма товаров
     * - остальные поля = null (так как доставка и налоги ещё не рассчитаны)
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

            ResponseEntity<ProductDto> response = storeServiceApi.getProductById(productId);

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
     * Рассчитывает полную стоимость заказа:
     * - сумма товаров
     * - НДС 10% от суммы товаров
     * - стоимость доставки (заглушка)
     */
    public Double calculateTotalCost(OrderDto orderDto) {
        // 1. Считаем стоимость товаров (переиспользуем существующую логику)
        PaymentDto productsPayment = calculateProductsTotal(orderDto);
        double productsTotal = productsPayment.getTotalPayment();

        // 2. НДС 10% от стоимости товаров
        double vat = productsTotal * 0.10;

        // 3. Стоимость доставки — заглушка. Позже заменить на реальный вызов сервиса доставки
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
     * Заглушка для стоимости доставки.
     * В будущем тут будет вызов DeliveryService / Feign-клиента.
     */
    private double getDeliveryCostStub(OrderDto orderDto) {
        // Пример простой логики: фиксированная стоимость или по весу/объёму
        // Пока вернём 50 рублей, как в примере из задания
        return 50.0;
    }
}
