package ru.yandex.practicum.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.yandex.practicum.api.WarehouseServiceApi;
import ru.yandex.practicum.dto.*;
import ru.yandex.practicum.entity.Order;
import ru.yandex.practicum.entity.OrderItem;
import ru.yandex.practicum.repository.OrderItemRepository;
import ru.yandex.practicum.repository.OrderRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final WarehouseServiceApi warehouseServiceApi; // Feign-клиент к складу

    @Transactional
    public OrderDto createOrder(CreateNewOrderRequest request) {
        ShoppingCartDto cart = request.getShoppingCart();
        if (cart == null || cart.getProducts().isEmpty()) {
            throw new IllegalArgumentException("Корзина не может быть пустой");
        }

        // 1. Проверка наличия на складе через Feign
        var response = warehouseServiceApi.check(cart);
        BookedProductsDto booked = response.getBody();
        if (booked == null) {
            // Если склад вернул 400 с исключением, Feign fallback/exception handler уже обработает это
            throw new IllegalStateException("Склад не вернул данные о наличии");
        }

        // 2. Расчёт цен (в реальном проекте это отдельный сервис/логика)
        Long productPrice = calculateProductPrice(cart.getProducts());
        Long deliveryPrice = calculateDeliveryPrice(booked);
        Long totalPrice = productPrice + deliveryPrice;

        // 3. Создаём заказ
        UUID orderId = UUID.randomUUID();
        Order order = Order.builder()
                .id(orderId)
                .shoppingCartId(cart.getShoppingCartId())
                .state(OrderStatus.NEW)
                .paymentId(null)
                .deliveryId(null)
                .totalPrice(totalPrice)
                .productPrice(productPrice)
                .deliveryPrice(deliveryPrice)
                .deliveryWeight(booked.getDeliveryWeight())
                .deliveryVolume(booked.getDeliveryVolume())
                .fragile(booked.isFragile())
                .createdAt(Instant.now())
                .build();

        order = orderRepository.save(order);

        // 4. Создаём позиции заказа (OrderItem)
        List<OrderItem> items = new ArrayList<>();
        for (Map.Entry<UUID, Long> entry : cart.getProducts().entrySet()) {
            OrderItem item = OrderItem.builder()
                    .order(order)
                    .productId(entry.getKey())
                    .quantity(entry.getValue())
                    .priceAtMoment(calculatePricePerUnit(entry.getKey())) // заглушка
                    .build();
            items.add(item);
        }
        orderItemRepository.saveAll(items);

        log.info("Заказ создан: orderId={}, totalPrice={}", orderId, totalPrice);
        return toDto(order, items);
    }

    // Заглушки для расчёта цен — замени на реальную логику или вызовы других сервисов
    private Long calculateProductPrice(Map<UUID, Long> products) {
        // В реальности: запрос к сервису цен, агрегация по productId * quantity
        return 1000L; // пример
    }

    private Long calculateDeliveryPrice(BookedProductsDto booked) {
        // Пример: зависит от веса/объёма/хрупкости
        double base = 200;
        double weightFactor = booked.getDeliveryWeight() * 5;
        return (long) (base + weightFactor);
    }

    private Long calculatePricePerUnit(UUID productId) {
        return 100L; // заглушка
    }

    private OrderDto toDto(Order order, List<OrderItem> items) {
        var productsMap = items.stream()
                .collect(Collectors.toMap(
                        OrderItem::getProductId,
                        item -> item.getQuantity().intValue()
                ));

        return OrderDto.builder()
                .orderId(order.getId())
                .shoppingCartId(order.getShoppingCartId())
                .products(productsMap)
                .paymentId(order.getPaymentId())
                .deliveryId(order.getDeliveryId())
                .state(order.getState())
                .deliveryWeight(order.getDeliveryWeight())
                .deliveryVolume(order.getDeliveryVolume())
                .fragile(order.getFragile())
                .totalPrice(order.getTotalPrice())
                .deliveryPrice(order.getDeliveryPrice())
                .productPrice(order.getProductPrice())
                .build();
    }
}
