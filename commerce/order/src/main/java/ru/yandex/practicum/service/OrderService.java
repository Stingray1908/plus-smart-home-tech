package ru.yandex.practicum.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.yandex.practicum.api.CartServiceApi;
import ru.yandex.practicum.api.DeliveryServiceApi;
import ru.yandex.practicum.api.PaymentServiceApi;
import ru.yandex.practicum.api.WarehouseServiceApi;
import ru.yandex.practicum.dto.*;
import ru.yandex.practicum.entity.Order;
import ru.yandex.practicum.entity.OrderItem;
import ru.yandex.practicum.exception.NoOrderFoundException;
import ru.yandex.practicum.exception.NotAuthorizedUserException;
import ru.yandex.practicum.repository.OrderItemRepository;
import ru.yandex.practicum.repository.OrderRepository;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final WarehouseServiceApi warehouseServiceApi;
    private final CartServiceApi cartServiceApi;
    private final PaymentServiceApi paymentServiceApi;
    private final DeliveryServiceApi deliveryServiceApi;

    // Этот сервис мы теперь используем только для смены статусов
    private final OrderStatusService statusService;

    //проверен
    @Transactional
    public OrderDto createOrder(CreateNewOrderRequest request) {
        ShoppingCartDto cart = request.getShoppingCart();
        if (cart == null || cart.getProducts().isEmpty()) {
            throw new IllegalArgumentException("Корзина не может быть пустой");
        }

        var response = warehouseServiceApi.check(cart);
        BookedProductsDto booked = response.getBody();

        Order order = Order.builder()
                .shoppingCartId(cart.getShoppingCartId())
                .state(OrderStatus.NEW)
                .totalPrice(0L)
                .productPrice(0L)
                .deliveryPrice(0L)
                .deliveryWeight(booked.getDeliveryWeight())
                .deliveryVolume(booked.getDeliveryVolume())
                .fragile(booked.isFragile())
                .build();

        order = orderRepository.save(order);

        Order finalOrder = order;
        List<OrderItem> items = cart.getProducts().entrySet().stream()
                .map(entry -> OrderItem.builder()
                        .order(finalOrder)
                        .productId(entry.getKey())
                        .quantity(entry.getValue())
                        .priceAtMoment(0L)
                        .build())
                .toList();

        long productPriceValue = calculateAndGetProductPrice(order, items);

        order.setProductPrice(productPriceValue);
        order.setTotalPrice(productPriceValue);
        orderRepository.save(order);

        orderItemRepository.saveAll(items);

        log.info("Заказ создан: orderId={}, productPrice={}, totalPrice={}",
                order.getId(), order.getProductPrice(), order.getTotalPrice());

        Order finalOrder1 = order;
        Map<UUID, List<OrderItem>> itemsByOrder = items.stream()
                .collect(Collectors.groupingBy(i -> finalOrder1.getId()));

        return toDto(order, itemsByOrder);
    }

    // верен
    public List<OrderDto> getOrdersByUsername(String username, int page, int size) {
        if (username == null || username.isBlank()) {
            throw new NotAuthorizedUserException("Username must not be empty", "Имя пользователя не должно быть пустым", 401);
        }

        var cartResponse = cartServiceApi.getShoppingCart(username);
        ShoppingCartDto cart = cartResponse.getBody();
        if (cart == null || cart.getShoppingCartId() == null) return Collections.emptyList();

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Order> orderPage = orderRepository.findByShoppingCartId(cart.getShoppingCartId(), pageable);
        if (orderPage.isEmpty()) return Collections.emptyList();

        List<UUID> orderIds = orderPage.getContent().stream().map(Order::getId).toList();
        List<OrderItem> allItems = orderItemRepository.findByOrderIdIn(orderIds);
        Map<UUID, List<OrderItem>> itemsByOrder = allItems.stream()
                .collect(Collectors.groupingBy(item -> item.getOrder().getId()));

        return orderPage.getContent().stream()
                .map(order -> toDto(order, itemsByOrder))
                .toList();
    }

    @Transactional
    public OrderDto returnOrder(ProductReturnRequest request) {
        UUID orderId = request.getOrderId();
        Map<UUID, Long> productsToReturn = request.getProducts();

        if (productsToReturn == null || productsToReturn.isEmpty()) {
            throw new IllegalArgumentException("Список возвращаемых товаров не может быть пустым");
        }

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new NoOrderFoundException("Заказ не найден: " + orderId, 400));

        OrderStatus currentState = order.getState();
        log.debug("Статус заказа {}: {}", orderId, currentState);

        // ГЛАВНАЯ ПРОВЕРКА: читаем из enum
        if (!currentState.canReturn()) {
            throw new IllegalStateException("Возврат невозможен для заказа со статусом: " + currentState);
        }

        List<OrderItem> items = orderItemRepository.findByOrderId(orderId);
        Map<UUID, OrderItem> itemMap = items.stream()
                .collect(Collectors.toMap(OrderItem::getProductId, i -> i));

        long newProductPrice = 0;
        Map<UUID, Long> finalProductsToReturnToWarehouse = new HashMap<>();

        for (var entry : productsToReturn.entrySet()) {
            UUID productId = entry.getKey();
            Long returnQty = entry.getValue();

            if (returnQty <= 0) continue;

            OrderItem item = itemMap.get(productId);
            if (item == null) {
                throw new IllegalArgumentException("Товар с ID " + productId + " отсутствует в заказе");
            }
            if (returnQty > item.getQuantity()) {
                throw new IllegalArgumentException(
                        "Нельзя вернуть " + returnQty + " шт. товара " + productId +
                                ", в заказе только " + item.getQuantity() + " шт."
                );
            }

            item.setQuantity(item.getQuantity() - returnQty);
            newProductPrice += item.getPriceAtMoment() * item.getQuantity();

            // ASSEMBLY_FAILED — товары не были забронированы на складе
            if (currentState != OrderStatus.ASSEMBLY_FAILED) {
                finalProductsToReturnToWarehouse.put(productId, returnQty);
            }
        }

        if (!finalProductsToReturnToWarehouse.isEmpty()) {
            log.info("Отправляем возврат товаров на склад для заказа {}", orderId);
            warehouseServiceApi.returnProducts(finalProductsToReturnToWarehouse);
        } else {
            log.info("Склад не вызывается: статус ASSEMBLY_FAILED или товаров для возврата нет");
        }

        order.setProductPrice(newProductPrice);
        order.setTotalPrice(newProductPrice + order.getDeliveryPrice());

        boolean allItemsReturned = items.stream().allMatch(i -> i.getQuantity() == 0);

        if (allItemsReturned) {
            order.setState(OrderStatus.CANCELED);
            log.info("Все товары возвращены, переводим заказ {} в CANCELED", orderId);
        } else {
            order.setState(OrderStatus.PRODUCT_RETURNED);
            log.info("Частичный возврат для заказа {}, статус PRODUCT_RETURNED", orderId);
        }

        orderRepository.save(order);
        orderItemRepository.saveAll(items);

        Map<UUID, List<OrderItem>> itemsByOrder = items.stream()
                .collect(Collectors.groupingBy(i -> order.getId()));

        return toDto(order, itemsByOrder);
    }


    private void sendReturnToWarehouse(Map<UUID, Long> returns) {
        for (var entry : returns.entrySet()) {
            warehouseServiceApi.addQuantity(AddProductToWarehouseRequest.builder()
                    .productId(entry.getKey())
                    .quantity(entry.getValue())
                    .build());
        }
    }

    @Transactional(readOnly = true)
    public OrderDto calculateDelivery(UUID orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> {
                    log.warn("Заказ не найден: orderId={}", orderId);
                    throw new NoOrderFoundException("Заказ не найден: " + orderId, 404);
                });

        Double deliveryPriceDouble = getDeliveryPriceFromServiceOrFallback(order);
        long deliveryPrice = deliveryPriceDouble.longValue();

        List<OrderItem> items = orderItemRepository.findByOrderId(orderId);
        Map<UUID, Long> productsMap = items.stream()
                .filter(i -> i.getQuantity() > 0)
                .collect(Collectors.toMap(
                        OrderItem::getProductId,
                        item -> item.getQuantity()
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
                .totalPrice(deliveryPrice)
                .deliveryPrice(deliveryPrice)
                .productPrice(0L)
                .build();
    }

    @Transactional(readOnly = true)
    public OrderDto calculateTotal(UUID orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> {
                    log.warn("Заказ не найден: orderId={}", orderId);
                    throw new NoOrderFoundException("Заказ не найден: " + orderId, 400);
                });

        List<OrderItem> items = orderItemRepository.findByOrderId(orderId);
        Map<UUID, Long> productsMap = items.stream()
                .filter(i -> i.getQuantity() > 0)
                .collect(Collectors.toMap(
                        OrderItem::getProductId,
                        item -> item.getQuantity()
                ));

        OrderDto orderDtoForCalculation = OrderDto.builder()
                .orderId(order.getId())
                .shoppingCartId(order.getShoppingCartId())
                .products(productsMap)
                .deliveryWeight(order.getDeliveryWeight())
                .deliveryVolume(order.getDeliveryVolume())
                .fragile(order.getFragile())
                .build();

        try {
            var totalCostResponse = paymentServiceApi.calculateTotalCost(orderDtoForCalculation);
            if (!totalCostResponse.getStatusCode().is2xxSuccessful()) {
                log.error("Сервис payment вернул ошибку при расчёте полной стоимости: {}", totalCostResponse.getStatusCodeValue());
                throw new IllegalStateException("Не удалось рассчитать полную стоимость заказа");
            }

            Double totalPriceDouble = totalCostResponse.getBody();
            if (totalPriceDouble == null) {
                throw new IllegalArgumentException("Пустой ответ от сервиса оплаты");
            }
            long totalPrice = totalPriceDouble.longValue();

            Double deliveryPriceDouble = getDeliveryPriceFromServiceOrFallback(order);
            long deliveryPrice = deliveryPriceDouble.longValue();
            long productPrice = Math.max(0, totalPrice - deliveryPrice);

            log.info("Полная стоимость заказа {} рассчитана: total={}, delivery={}, product={}",
                    orderId, totalPrice, deliveryPrice, productPrice);

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
                    .totalPrice(totalPrice)
                    .deliveryPrice(deliveryPrice)
                    .productPrice(productPrice)
                    .build();

        } catch (IllegalArgumentException e) {
            log.warn("Не удалось получить полную стоимость от payment, возвращаем текущие цены из БД. Причина: {}", e.toString());
            Map<UUID, Long> currentProducts = items.stream()
                    .filter(i -> i.getQuantity() > 0)
                    .collect(Collectors.toMap(
                            OrderItem::getProductId,
                            item -> item.getQuantity()
                    ));

            return OrderDto.builder()
                    .orderId(order.getId())
                    .shoppingCartId(order.getShoppingCartId())
                    .products(currentProducts)
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

    private OrderDto toDto(Order order, Map<UUID, List<OrderItem>> itemsByOrder) {
        List<OrderItem> currentItems = itemsByOrder.getOrDefault(order.getId(), Collections.emptyList());

        Map<UUID, Long> productsMap = currentItems.stream()
                .filter(i -> i.getQuantity() > 0)
                .collect(Collectors.toMap(
                        OrderItem::getProductId,
                        OrderItem::getQuantity
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

    private Double getDeliveryPriceFromServiceOrFallback(Order order) {
        try {
            OrderDto requestDto = OrderDto.builder()
                    .orderId(order.getId())
                    .deliveryWeight(order.getDeliveryWeight())
                    .deliveryVolume(order.getDeliveryVolume())
                    .fragile(order.getFragile())
                    .build();

            var resp = deliveryServiceApi.calculateDeliveryCost(requestDto);
            log.debug("Ответ от delivery/cost: status={}", resp.getStatusCodeValue());

            if (!resp.getStatusCode().is2xxSuccessful()) {
                log.warn("delivery вернул не 200: status={}, orderId={}", resp.getStatusCodeValue(), order.getDeliveryId());
                throw new IllegalStateException("Не удалось рассчитать доставку (статус: " + resp.getStatusCodeValue() + ")");
            }

            Double price = resp.getBody();
            if (price == null) {
                log.warn("Пустой body от delivery для заказа: {}", order.getDeliveryId());
                throw new IllegalStateException("Пустой ответ от сервиса доставки");
            }

            log.info("Реальная стоимость доставки получена: {}", price);
            return price;

        } catch (Exception e) {
            throw new IllegalArgumentException("Не удалось получить стоимость доставки от сервиса delivery, используем формулу как fallback. Причина: {}");
        }
    }

    @Transactional
    private OrderDto markOrderStatus(UUID orderId, OrderStatus targetStatus, String actionLog) {
        if (orderId == null) {
            throw new IllegalArgumentException("orderId обязателен");
        }

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new NoOrderFoundException("Заказ не найден: " + orderId, 404));

        statusService.transitionTo(orderId, targetStatus);

        List<OrderItem> items = orderItemRepository.findByOrderId(orderId);
        Map<UUID, List<OrderItem>> itemsByOrder = items.stream()
                .collect(Collectors.groupingBy(i -> i.getOrder().getId()));

        log.info(actionLog, orderId);
        return toDto(order, itemsByOrder);
    }

    public OrderDto markPaymentFailed(UUID orderId) {
        return markOrderStatus(orderId, OrderStatus.PAYMENT_FAILED, "Оплата заказа {} завершилась ошибкой, статус установлен: PAYMENT_FAILED");
    }

    public OrderDto markDelivered(UUID orderId) {
        return markOrderStatus(orderId, OrderStatus.DELIVERED, "Заказ {} доставлен, статус установлен: DELIVERED");
    }

    public OrderDto markDeliveryFailed(UUID orderId) {
        return markOrderStatus(orderId, OrderStatus.DELIVERY_FAILED, "Доставка заказа {} завершилась ошибкой, статус установлен: DELIVERY_FAILED");
    }

    public OrderDto markAssembled(UUID orderId) {
        return markOrderStatus(orderId, OrderStatus.ASSEMBLED, "Заказ {} собран, статус установлен: ASSEMBLED");
    }

    public OrderDto markAssemblyFailed(UUID orderId) {
        return markOrderStatus(orderId, OrderStatus.ASSEMBLY_FAILED, "Сборка заказа {} завершилась ошибкой, статус установлен: ASSEMBLY_FAILED");
    }

    public OrderDto markCompleted(UUID orderId) {
        return markOrderStatus(orderId, OrderStatus.COMPLETED, "Сборка заказа {} завершилась, статус установлен: COMPLETED");
    }

    /**
     * Вызывает сервис оплаты для расчёта общей стоимости товаров в заказе.
     * Возвращает округлённую сумму в Long.
     */
    private long calculateAndGetProductPrice(Order order, List<OrderItem> items) {
        OrderDto orderForCalculation = toDto(order, items.stream()
                .collect(Collectors.groupingBy(i -> order.getId())));

        ResponseEntity<Double> paymentResponse = paymentServiceApi.calculateProductCost(orderForCalculation);
        Double totalCost = paymentResponse.getBody();

        if (totalCost == null) {
            throw new IllegalStateException("Сервис оплаты вернул null вместо стоимости");
        }

        return Math.round(totalCost);
    }
}
