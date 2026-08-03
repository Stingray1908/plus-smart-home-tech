package ru.yandex.practicum.service;

import jakarta.ws.rs.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


import ru.yandex.practicum.api.CartServiceApi;
import ru.yandex.practicum.api.WarehouseServiceApi;
import ru.yandex.practicum.dto.*;
import ru.yandex.practicum.entity.Order;
import ru.yandex.practicum.entity.OrderItem;
import ru.yandex.practicum.exception.NotAuthorizedUserException;
import ru.yandex.practicum.repository.OrderItemRepository;
import ru.yandex.practicum.repository.OrderRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
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
    private final WarehouseServiceApi warehouseServiceApi;
    private final CartServiceApi cartServiceApi;

    // Раскомментируй, когда будут готовы сервисы доставки и оплаты:
    // private final DeliveryServiceApi deliveryServiceApi;
    // private final PaymentServiceApi paymentServiceApi;

    @Transactional
    public OrderDto createOrder(CreateNewOrderRequest request) {
        ShoppingCartDto cart = request.getShoppingCart();
        if (cart == null || cart.getProducts().isEmpty()) {
            throw new IllegalArgumentException("Корзина не может быть пустой");
        }

        // 1. Проверка наличия на складе
        var response = warehouseServiceApi.check(cart);
        BookedProductsDto booked = response.getBody();
        if (booked == null) {
            throw new IllegalStateException("Склад не вернул данные о наличии");
        }

        // 2. Резервирование доставки (раскомментируй при готовности сервиса)
        /*
        var deliveryResponse = deliveryServiceApi.reserveDelivery(booked);
        DeliveryResponseDto delivery = deliveryResponse.getBody();
        if (delivery == null) {
            throw new IllegalStateException("Сервис доставки не вернул корректный ответ");
        }
        Long deliveryPrice = delivery.getPrice();
        */
        // Заглушка для доставки
        Long deliveryPrice = calculateDeliveryPrice(booked);

        // 3. Инициирование оплаты (раскомментируй при готовности сервиса)
        Long productPrice = calculateProductPrice(cart.getProducts());
        Long totalPrice = productPrice + deliveryPrice;

        /*
        PaymentRequestDto paymentRequest = PaymentRequestDto.builder()
                .amount(totalPrice)
                .build();

        var paymentResponse = paymentServiceApi.initiatePayment(paymentRequest);
        PaymentResponseDto payment = paymentResponse.getBody();
        if (payment == null) {
            throw new IllegalStateException("Сервис оплаты не вернул корректный ответ");
        }
        UUID paymentId = payment.getPaymentId();
        UUID deliveryId = delivery.getDeliveryId();
        */
        UUID paymentId = null;
        UUID deliveryId = null;

        // 4. Создаём заказ
        UUID orderId = UUID.randomUUID();
        Order order = Order.builder()
                .id(orderId)                 // ✅ исправлено
                .shoppingCartId(cart.getShoppingCartId())
                .state(OrderStatus.NEW)
                .paymentId(paymentId)
                .deliveryId(deliveryId)
                .totalPrice(totalPrice)
                .productPrice(productPrice)
                .deliveryPrice(deliveryPrice)
                .deliveryWeight(booked.getDeliveryWeight())
                .deliveryVolume(booked.getDeliveryVolume())
                .fragile(booked.isFragile())
                .createdAt(Instant.now())
                .build();

        order = orderRepository.save(order);

        // 5. Создаём позиции заказа (OrderItem)
        List<OrderItem> items = new ArrayList<>();
        for (Map.Entry<UUID, Long> entry : cart.getProducts().entrySet()) {
            OrderItem item = OrderItem.builder()
                    .order(order)
                    .productId(entry.getKey())
                    .quantity(entry.getValue())
                    .priceAtMoment(calculatePricePerUnit(entry.getKey()))
                    .build();
            items.add(item);
        }
        orderItemRepository.saveAll(items);

        Map<UUID, List<OrderItem>> itemsByOrder = items.stream()
                .collect(Collectors.groupingBy(item -> item.getOrder().getId()));


        log.info("Заказ создан: orderId={}, totalPrice={}", order.getCreatedAt(), totalPrice);

        // Вызываем единый метод. Ключ - order.getOrderId(), значение - список позиций этого заказа
        return toDto(order, itemsByOrder);
    }

    /**
     * Получить заказы пользователя с пагинацией и подгрузкой товаров одним запросом.
     */
    public List<OrderDto> getOrdersByUsername(String username, int page, int size) {
        if (username == null || username.isBlank()) {
            log.warn("Запрос без username");
            throw new NotAuthorizedUserException(
                    "Username must not be empty",
                    "Имя пользователя не должно быть пустым",
                    401
            );
        }

        // 1. Получаем корзину по username через Feign
        var cartResponse = cartServiceApi.getShoppingCart(username);
        ShoppingCartDto cart = cartResponse.getBody();
        if (cart == null) {
            // Если корзины нет — возвращаем пустой список, а не ошибку (логично для «нет заказов»)
            return Collections.emptyList();
        }
        UUID shoppingCartId = cart.getShoppingCartId();
        if (shoppingCartId == null) {
            return Collections.emptyList();
        }

        Pageable pageable = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Direction.DESC, "createdAt")
        );

        // 2. Ищем заказы по shoppingCartId
        Page<Order> orderPage = orderRepository.findByShoppingCartId(shoppingCartId, pageable);
        List<Order> orders = orderPage.getContent();

        if (orders.isEmpty()) {
            return Collections.emptyList();
        }

        // 3. Собираем IDs заказов
        List<UUID> orderIds = orders.stream()
                .map(Order::getId)
                .collect(Collectors.toList());

        // 4. Одним запросом получаем ВСЕ позиции для этих заказов
        List<OrderItem> items = orderItemRepository.findByOrderIdIn(orderIds);

        // 5. Группируем позиции по orderId
        Map<UUID, List<OrderItem>> itemsByOrder = items.stream()
                .collect(Collectors.groupingBy(item -> item.getOrder().getId()));

        // 6. Маппим в DTO
        return orders.stream()
                .map(order -> toDto(order, itemsByOrder))
                .collect(Collectors.toList());
    }

    @Transactional
    public OrderDto returnOrder(ProductReturnRequest request) {
        if (request.getOrderId() == null || request.getProducts() == null || request.getProducts().isEmpty()) {
            throw new IllegalArgumentException("Некорректный запрос на возврат");
        }

        Order order = orderRepository.findById(request.getOrderId())
                .orElseThrow(() -> new NotFoundException("Заказ не найден: " + request.getOrderId()));

        // Разрешаем возврат только для NEW и ASSEMBLED
        if (!List.of(OrderStatus.NEW, OrderStatus.ASSEMBLED).contains(order.getState())) {
            throw new IllegalStateException("Возврат невозможен для статуса: " + order.getState());
        }

        List<OrderItem> items = orderItemRepository.findByOrderId(request.getOrderId());
        Map<UUID, OrderItem> itemMap = items.stream()
                .collect(Collectors.toMap(OrderItem::getProductId, i -> i));

        long newProductPrice = 0;

        for (var entry : request.getProducts().entrySet()) {
            UUID productId = entry.getKey();
            Long returnQty = entry.getValue();

            if (returnQty <= 0) {
                continue;
            }

            OrderItem item = itemMap.get(productId);
            if (item == null) {
                throw new IllegalArgumentException("Товар " + productId + " отсутствует в заказе");
            }

            long currentQty = item.getQuantity();
            if (returnQty > currentQty) {
                throw new IllegalArgumentException(
                        "Нельзя вернуть больше, чем было в заказе: запрошено " + returnQty + ", в заказе " + currentQty);
            }

            long newQty = currentQty - returnQty;
            item.setQuantity(newQty);

            long itemTotal = item.getPriceAtMoment() * newQty;
            newProductPrice += itemTotal;
        }

        long deliveryPrice = order.getDeliveryPrice();
        long newTotalPrice = newProductPrice + deliveryPrice;

        order.setProductPrice(newProductPrice);
        order.setTotalPrice(newTotalPrice);

        boolean allZero = items.stream().allMatch(i -> i.getQuantity() == 0);
        if (allZero) {
            order.setState(OrderStatus.CANCELED);
        }

        // Отправляем возврат на склад отдельными запросами
        sendReturnToWarehouse(order, request.getProducts());

        orderRepository.save(order);
        orderItemRepository.saveAll(items);

        Map<UUID, List<OrderItem>> itemsByOrder = items.stream()
                .collect(Collectors.groupingBy(i -> order.getId()));

        return toDto(order, itemsByOrder);
    }



    // Вспомогательный метод для маппинга с уже загруженными позициями
    private OrderDto toDto(Order order, Map<UUID, List<OrderItem>> itemsByOrder) {
        List<OrderItem> orderItems = itemsByOrder.getOrDefault(order.getDeliveryId(), Collections.emptyList());

        Map<UUID, Integer> productsMap = orderItems.stream()
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

    private void sendReturnToWarehouse(Order order, Map<UUID, Long> returns) {
        log.info("Отправка возврата на склад: orderId={}, products={}", order.getId(), returns);

        for (var entry : returns.entrySet()) {
            UUID productId = entry.getKey();
            Long quantity = entry.getValue();

            if (quantity <= 0) {
                continue; // защита от некорректных данных
            }

            var request = AddProductToWarehouseRequest.builder()
                    .productId(productId)
                    .quantity(quantity)
                    .build();

            // Отдельный вызов для каждого товара
            warehouseServiceApi.addQuantity(request);
        }
    }




    // Заглушки для расчёта цен
    private Long calculateProductPrice(Map<UUID, Long> products) {
        return 1000L;
    }

    private Long calculateDeliveryPrice(BookedProductsDto booked) {
        double base = 200;
        double weightFactor = booked.getDeliveryWeight() * 5;
        return (long) (base + weightFactor);
    }

    private Long calculatePricePerUnit(UUID productId) {
        return 100L;
    }
}
