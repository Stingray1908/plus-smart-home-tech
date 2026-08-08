package ru.yandex.practicum.service;

import feign.FeignException;
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
import ru.yandex.practicum.exception.*;
import ru.yandex.practicum.repository.OrderItemRepository;
import ru.yandex.practicum.repository.OrderRepository;

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
    private final OrderStatusService statusService;

    /**
     * Оплата заказа: только если статус ASSEMBLED и все цены рассчитаны.
     */
    @Transactional
    public OrderDto payOrder(UUID orderId) {
        Order order = findOrderOrFail(orderId);

        validateOrderForPayment(order);

        log.info("Начинаем процесс оплаты для заказа {} (статус ASSEMBLED)", orderId);

        Map<UUID, Long> productsMap = buildProductsMap(orderId);

        OrderDto requestDto = buildOrderDtoForPayment(order, productsMap);

        ResponseEntity<PaymentDto> paymentResponse = paymentServiceApi.createPayment(requestDto);
        handlePaymentResponse(paymentResponse, order);

        return toDto(order, buildOrderItemsList(orderId));
    }

    /**
     * Создание заказа: проверка наличия, создание доставки, расчёт доставки, запуск оплаты.
     */
    @Transactional
    public OrderDto createOrder(CreateNewOrderRequest request) {
        ShoppingCartDto cart = validateCartNotEmpty(request.getShoppingCart());

        // 1. Проверка наличия на складе
        var checkResponse = warehouseServiceApi.check(cart);
        if (!checkResponse.getStatusCode().is2xxSuccessful()) {
            throw new IllegalStateException("Не удалось проверить наличие товаров на складе");
        }
        BookedProductsDto booked = checkResponse.getBody();
        if (booked == null) {
            throw new IllegalStateException("Склад не вернул данные о бронировании");
        }

        double totalWeight = booked.getDeliveryWeight();
        double totalVolume = booked.getDeliveryVolume();
        boolean fragile = booked.isFragile();

        // 2. Получаем адрес склада
        AddressDto warehouseAddress = warehouseServiceApi.getAddress()
                .getBody();

        // 3. Адрес доставки
        AddressDto deliveryAddress = request.getDeliveryAddress();
        if (deliveryAddress == null) {
            throw new IllegalArgumentException("Адрес доставки обязателен для создания заказа");
        }

        // 4. Создаём заказ (статус NEW)
        Order order = Order.builder()
                .shoppingCartId(cart.getShoppingCartId())
                .state(OrderStatus.NEW)
                .totalPrice(0d)
                .productPrice(0d)
                .deliveryPrice(0d)
                .deliveryWeight(totalWeight)
                .deliveryVolume(totalVolume)
                .fragile(fragile)
                .build();
        order = orderRepository.save(order);

        // 5. Создаём позиции заказа
        List<OrderItem> items = buildOrderItems(order, cart.getProducts());
        orderItemRepository.saveAll(items);

        double productPriceValue = calculateProductPrice(order, items);
        order.setProductPrice(productPriceValue);
        order.setTotalPrice(productPriceValue); // пока без доставки
        orderRepository.save(order);

        // 6. Создаём доставку
        DeliveryDto deliveryDto = DeliveryDto.builder()
                .fromAddress(warehouseAddress)
                .toAddress(deliveryAddress)
                .orderId(order.getId())
                .deliveryState(DeliveryState.CREATED)
                .build();

        var deliveryResponse = deliveryServiceApi.createOrUpdateDelivery(deliveryDto);
        if (!deliveryResponse.getStatusCode().is2xxSuccessful() || deliveryResponse.getBody() == null) {
            throw new IllegalStateException("Не удалось создать доставку в сервисе доставки");
        }
        deliveryDto = deliveryResponse.getBody();
        UUID deliveryId = deliveryDto.getDeliveryId();
        if (deliveryId == null) {
            throw new IllegalStateException("Сервис доставки не вернул deliveryId");
        }

        order.setDeliveryId(deliveryId);
        orderRepository.save(order);

        // 7. Расчёт стоимости доставки
        Double deliveryPriceValue = calculateDeliveryCost(order);
        double deliveryPriceLong = Math.round(deliveryPriceValue);

        order.setDeliveryPrice(deliveryPriceLong);
        orderRepository.save(order);

        // 8. Запуск процесса оплаты
        Map<UUID, Long> productsMap = cart.getProducts();
        OrderDto orderDtoForPayment = OrderDto.builder()
                .orderId(order.getId())
                .shoppingCartId(order.getShoppingCartId())
                .products(productsMap)
                .deliveryWeight(order.getDeliveryWeight())
                .deliveryVolume(order.getDeliveryVolume())
                .fragile(order.getFragile())
                .deliveryPrice(deliveryPriceValue)
                .build();

        var paymentResponseForOrder = paymentServiceApi.createPayment(orderDtoForPayment);
        if (!paymentResponseForOrder.getStatusCode().is2xxSuccessful() || paymentResponseForOrder.getBody() == null) {
            throw new IllegalStateException("Не удалось запустить процесс оплаты: платёж не создан");
        }
        PaymentDto paymentDto = paymentResponseForOrder.getBody();
        if (paymentDto.getPaymentId() == null) {
            throw new IllegalStateException("Платёж создан, но не вернул paymentId");
        }

        order.setPaymentId(paymentDto.getPaymentId());

        Double finalTotalPrice = (paymentDto.getTotalPayment() != null)
                ? Math.round(paymentDto.getTotalPayment())
                : 0d;
        order.setTotalPrice(finalTotalPrice);

        orderRepository.save(order);

        Order finalOrder = order;
        return toDto(order, items.stream()
                .collect(Collectors.groupingBy(i -> finalOrder.getId())));
    }

    /**
     * Получение заказов пользователя.
     */
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

    /**
     * Возврат товара.
     */
    @Transactional
    public OrderDto returnOrder(ProductReturnRequest request) {
        UUID orderId = request.getOrderId();
        Map<UUID, Long> productsToReturn = request.getProducts();

        if (productsToReturn == null || productsToReturn.isEmpty()) {
            throw new IllegalArgumentException("Список возвращаемых товаров не может быть пустым");
        }

        Order order = findOrderOrFail(orderId);
        OrderStatus currentState = order.getState();

        if (!currentState.canReturn()) {
            throw new IllegalStateException("Возврат невозможен для заказа со статусом: " + currentState);
        }

        List<OrderItem> items = orderItemRepository.findByOrderId(orderId);
        Map<UUID, OrderItem> itemMap = items.stream()
                .collect(Collectors.toMap(OrderItem::getProductId, i -> i));

        double newProductPrice = 0;
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

        return toDto(order, items.stream()
                .collect(Collectors.groupingBy(i -> order.getId())));
    }

    /**
     * Расчёт доставки.
     */
    @Transactional
    public OrderDto calculateDelivery(UUID orderId) {
        log.debug("Начало расчёта доставки для заказа: {}", orderId);

        Order order = findOrderOrFail(orderId);

        if (order.getDeliveryWeight() == null && order.getDeliveryVolume() == null) {
            throw new IllegalArgumentException("Недостаточно данных для расчёта доставки (вес/объём)");
        }

        OrderDto requestDto = OrderDto.builder()
                .orderId(order.getId())
                .deliveryWeight(order.getDeliveryWeight())
                .deliveryVolume(order.getDeliveryVolume())
                .fragile(order.getFragile())
                .build();

        log.info("Вызов сервиса доставки для заказа {}", orderId);
        ResponseEntity<Double> response;
        try {
            response = deliveryServiceApi.calculateDeliveryCost(requestDto);
        } catch (FeignException e) {
            log.error("Сервис доставки недоступен. Заказ: {}. Ошибка: {}", orderId, e.getMessage());
            throw new IllegalStateException("Сервис доставки (delivery) временно недоступен");
        }

        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new IllegalStateException(
                    "Не удалось рассчитать доставку: сервис вернул статус " + response.getStatusCode()
            );
        }

        double deliveryPrice = Math.round(response.getBody());
        log.info("Стоимость доставки для заказа {}: {}", orderId, deliveryPrice);

        order.setDeliveryPrice(deliveryPrice);
        orderRepository.save(order);

        return toDto(order, buildOrderItemsList(orderId));
    }

    /**
     * Расчёт итоговой стоимости.
     */
    @Transactional
    public OrderDto calculateTotal(UUID orderId) {
        Order order = findOrderOrFail(orderId);

        if (order.getDeliveryPrice() == null || order.getDeliveryPrice() <= 0) {
            throw new NotEnoughInfoInOrderToCalculateException(
                    "Сначала рассчитайте доставку через /api/v1/order/calculate/delivery", 400
            );
        }

        List<OrderItem> items = orderItemRepository.findByOrderId(orderId);
        Map<UUID, Long> productsMap = items.stream()
                .filter(i -> i.getQuantity() > 0)
                .collect(Collectors.toMap(
                        OrderItem::getProductId,
                        OrderItem::getQuantity
                ));

        OrderDto requestDto = OrderDto.builder()
                .orderId(order.getId())
                .shoppingCartId(order.getShoppingCartId())
                .products(productsMap)
                .deliveryWeight(order.getDeliveryWeight())
                .deliveryVolume(order.getDeliveryVolume())
                .fragile(order.getFragile())
                .deliveryPrice(order.getDeliveryPrice())
                .build();

        ResponseEntity<Double> response = paymentServiceApi.calculateTotalCost(requestDto);
        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new IllegalStateException("Сервис оплаты не смог рассчитать сумму");
        }

        Double totalAmount = response.getBody();
        if (totalAmount == null) {
            throw new IllegalArgumentException("Пустой ответ от сервиса оплаты");
        }

        order.setTotalPrice(totalAmount);
        orderRepository.save(order);

        return toDto(order, items.stream()
                .collect(Collectors.groupingBy(i -> order.getId())));
    }

    @Transactional
    public OrderDto markAssembled(UUID orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Заказ не найден: " + orderId));

        if (!order.getState().equals(OrderStatus.NEW)) {
            throw new IllegalStateException(
                    "Нельзя собирать заказ со статусом: " + order.getState()
            );
        }

        List<OrderItem> items = orderItemRepository.findByOrderId(orderId);
        if (items.isEmpty()) {
            throw new IllegalStateException("В заказе нет товаров");
        }

        Map<UUID, Long> productsMap = items.stream()
                .collect(Collectors.toMap(
                        OrderItem::getProductId,
                        OrderItem::getQuantity
                ));

        AssemblyProductsForOrderRequest request = new AssemblyProductsForOrderRequest(orderId, productsMap);

        ResponseEntity<BookedProductsDto> response = warehouseServiceApi.assembleOrder(request);
        BookedProductsDto booked = response.getBody();
        if (booked == null) {
            throw new IllegalStateException("Склад не вернул данные о бронировании");
        }

        order.setDeliveryWeight(booked.getDeliveryWeight());
        order.setDeliveryVolume(booked.getDeliveryVolume());
        order.setFragile(booked.isFragile());

        order.setState(OrderStatus.ASSEMBLED);
        orderRepository.save(order);

        log.info("Заказ {} собран: вес={}, объём={}, хрупкий={}",
                order.getId(), order.getDeliveryWeight(), order.getDeliveryVolume(), order.getFragile());

        Map<UUID, List<OrderItem>> itemsByOrder = items.stream()
                .collect(Collectors.groupingBy(i -> order.getId()));

        return toDto(order, itemsByOrder);
    }

    @Transactional
    public OrderDto markAssemblyFailed(UUID orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Заказ не найден: " + orderId));

        if (!order.getState().equals(OrderStatus.NEW) && !order.getState().equals(OrderStatus.ASSEMBLED)) {
            throw new IllegalStateException(
                    "Недопустимый статус для отмены сборки: текущий статус = " + order.getState()
            );
        }

        if (order.getState().equals(OrderStatus.NEW)) {
            order.setState(OrderStatus.ASSEMBLY_FAILED);
            orderRepository.save(order);
            log.info("Заказ {} переведён в ASSEMBLY_FAILED (статус был NEW, брони не было)", orderId);
            return toDto(order, Map.of());
        }

        List<OrderItem> items = orderItemRepository.findByOrderId(orderId);

        if (!items.isEmpty()) {
            Map<UUID, Long> productsToReturn = items.stream()
                    .collect(Collectors.toMap(
                            OrderItem::getProductId,
                            OrderItem::getQuantity,
                            (existing, replacement) -> existing
                    ));

            try {
                warehouseServiceApi.returnProducts(productsToReturn);
                log.info("Остатки товаров для заказа {} возвращены на склад", orderId);
            } catch (Exception e) {
                log.error("Не удалось вернуть товары на склад для заказа {}. Статус не изменён.", orderId, e);
                throw new IllegalStateException(
                        "Склад недоступен, невозможно откатить бронь товаров"
                );
            }
        } else {
            log.warn("В заказе {} нет позиций, хотя статус ASSEMBLED", orderId);
        }

        order.setState(OrderStatus.ASSEMBLY_FAILED);
        order.setDeliveryWeight(0.0);
        order.setDeliveryVolume(0.0);
        order.setFragile(false);
        orderRepository.save(order);
        log.info("Сборка заказа {} отменена, статус: ASSEMBLY_FAILED", order.getId());

        return toDto(order, items.stream()
                .collect(Collectors.groupingBy(i -> order.getId())));
    }

    public OrderDto markDelivered(UUID orderId) {
        return markOrderStatus(orderId, OrderStatus.DELIVERED, "Заказ {} доставлен, статус установлен: DELIVERED");
    }

    public OrderDto markDeliveryFailed(UUID orderId) {
        return markOrderStatus(orderId, OrderStatus.DELIVERY_FAILED, "Доставка заказа {} завершилась ошибкой, статус установлен: DELIVERY_FAILED");
    }

    @Transactional
    public OrderDto markOrderPaymentAsPaid(UUID orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new NoOrderFoundException("Заказ не найден для обновления статуса", 404));

        order.setState(OrderStatus.PAID);
        orderRepository.save(order);
        log.info("Заказ {} помечен как оплаченный, статус: {}", orderId, order.getState());

        return buildOrderDto(order);
    }

    @Transactional
    public OrderDto markOrderPaymentAsFailed(UUID orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new NoOrderFoundException("Заказ не найден для обновления статуса", 404));

        // В твоём коде ты ставишь NEW — оставь так, если это по ТЗ
        order.setState(OrderStatus.NEW);
        orderRepository.save(order);
        log.info("Заказ {} помечен как ошибка оплаты, статус: {}", orderId, order.getState());

        return buildOrderDto(order);
    }


// ------------------------------------------------------------------
// Вспомогательные методы (вынесены для чистоты кода)
// ------------------------------------------------------------------

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

    private OrderDto buildOrderDto(Order order) {
        List<OrderItem> items = orderItemRepository.findByOrderId(order.getId());
        Map<UUID, Long> productsMap = items.stream()
                .filter(i -> i.getQuantity() > 0)
                .collect(Collectors.toMap(OrderItem::getProductId, OrderItem::getQuantity));

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


    private Order findOrderOrFail(UUID orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> {
                    log.warn("Заказ не найден: orderId={}", orderId);
                    throw new NoOrderFoundException("Заказ не найден: " + orderId, 404);
                });
    }

    private void validateOrderForPayment(Order order) {
        if (order.getState() != OrderStatus.ASSEMBLED) {
            log.warn("Нельзя оплатить заказ {}: статус={}, ожидается ASSEMBLED", order.getId(), order.getState());
            throw new IllegalStateException(
                    "Оплата возможна только для заказа со статусом ASSEMBLED. Текущий статус: " + order.getState()
            );
        }
        if (order.getDeliveryPrice() == null || order.getDeliveryPrice() <= 0) {
            throw new NotEnoughInfoInOrderToCalculateException(
                    "Сначала рассчитайте доставку через /api/v1/order/calculate/delivery", 400
            );
        }
        if (order.getTotalPrice() == null || order.getTotalPrice() <= 0) {
            throw new NotEnoughInfoInOrderToCalculateException(
                    "Сначала рассчитайте полную стоимость через /api/v1/order/calculate/total", 400
            );
        }
    }

    private ShoppingCartDto validateCartNotEmpty(ShoppingCartDto cart) {
        if (cart == null || cart.getProducts() == null || cart.getProducts().isEmpty()) {
            throw new IllegalArgumentException("Корзина не может быть пустой");
        }
        return cart;
    }

    private Map<UUID, Long> buildProductsMap(UUID orderId) {
        return orderItemRepository.findByOrderId(orderId).stream()
                .filter(i -> i.getQuantity() > 0)
                .collect(Collectors.toMap(OrderItem::getProductId, OrderItem::getQuantity));
    }

    private List<OrderItem> buildOrderItems(Order order, Map<UUID, Long> products) {
        return products.entrySet().stream()
                .map(entry -> OrderItem.builder()
                        .order(order)
                        .productId(entry.getKey())
                        .quantity(entry.getValue())
                        .priceAtMoment(0L) // цена будет проставлена позже
                        .build())
                .toList();
    }

    private long calculateProductPrice(Order order, List<OrderItem> items) {
        Map<UUID, Long> productsMap = items.stream()
                .filter(i -> i.getQuantity() > 0)
                .collect(Collectors.toMap(
                        OrderItem::getProductId,
                        OrderItem::getQuantity
                ));

        OrderDto requestDto = OrderDto.builder()
                .orderId(order.getId())
                .products(productsMap)
                .build();

        ResponseEntity<Double> response = paymentServiceApi.calculateProductCost(requestDto);
        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new IllegalStateException(
                    "Не удалось рассчитать стоимость товаров: сервис вернул статус " + response.getStatusCode()
            );
        }

        double total = response.getBody();
        return Math.round(total);
    }


    private Double calculateDeliveryCost(Order order) {
        OrderDto requestDto = OrderDto.builder()
                .orderId(order.getId())
                .deliveryWeight(order.getDeliveryWeight())
                .deliveryVolume(order.getDeliveryVolume())
                .fragile(order.getFragile())
                .build();

        var response = deliveryServiceApi.calculateDeliveryCost(requestDto);
        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new IllegalStateException(
                    "Не удалось рассчитать стоимость доставки: сервис вернул статус " + response.getStatusCode()
            );
        }
        return response.getBody();
    }

    private OrderDto buildOrderDtoForPayment(Order order, Map<UUID, Long> productsMap) {
        return OrderDto.builder()
                .orderId(order.getId())
                .shoppingCartId(order.getShoppingCartId())
                .products(productsMap)
                .deliveryId(order.getDeliveryId())
                .paymentId(order.getPaymentId())
                .state(order.getState())
                .deliveryWeight(order.getDeliveryWeight())
                .deliveryVolume(order.getDeliveryVolume())
                .fragile(order.getFragile())
                .totalPrice(order.getTotalPrice())
                .deliveryPrice(order.getDeliveryPrice())
                .productPrice(order.getProductPrice())
                .build();
    }

    private void handlePaymentResponse(ResponseEntity<PaymentDto> response, Order order) {
        if (!response.getStatusCode().is2xxSuccessful()) {
            log.error("Сервис оплаты вернул ошибку при создании платежа: {}", response.getStatusCode());
            throw new IllegalStateException("Не удалось создать платёж в сервисе оплаты");
        }

        PaymentDto paymentDto = response.getBody();
        if (paymentDto == null) {
            throw new IllegalArgumentException("Пустой ответ от сервиса оплаты");
        }

        order.setPaymentId(paymentDto.getPaymentId());
        order.setState(OrderStatus.ON_PAYMENT);
        // save будет вызван в конце транзакции
    }

    private Map<UUID, List<OrderItem>> buildOrderItemsList(UUID orderId) {
        List<OrderItem> items = orderItemRepository.findByOrderId(orderId);
        return items.stream()
                .collect(Collectors.groupingBy(i -> i.getOrder().getId()));
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
}

