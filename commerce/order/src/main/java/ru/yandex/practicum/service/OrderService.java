package ru.yandex.practicum.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.yandex.practicum.api.CartServiceApi;
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

    // Этот сервис мы теперь используем только для смены статусов
    private final OrderStatusService statusService;

    @Transactional
    public OrderDto createOrder(CreateNewOrderRequest request) {
        ShoppingCartDto cart = request.getShoppingCart();
        if (cart == null || cart.getProducts().isEmpty()) {
            throw new IllegalArgumentException("Корзина не может быть пустой");
        }

        var response = warehouseServiceApi.check(cart);
        BookedProductsDto booked = response.getBody();

        Long productPrice = calculateProductPrice(cart.getProducts());
        Long deliveryPrice = calculateDeliveryPrice(booked);
        Long totalPrice = productPrice + deliveryPrice;

        UUID orderId = UUID.randomUUID();
        Order order = Order.builder()
                .id(orderId)
                .shoppingCartId(cart.getShoppingCartId())
                .state(OrderStatus.NEW) // Начальный статус
                .totalPrice(totalPrice)
                .productPrice(productPrice)
                .deliveryPrice(deliveryPrice)
                .deliveryWeight(booked.getDeliveryWeight())
                .deliveryVolume(booked.getDeliveryVolume())
                .fragile(booked.isFragile())
                .createdAt(Instant.now())
                .build();
        order = orderRepository.save(order);

        Order finalOrder = order;
        List<OrderItem> items = cart.getProducts().entrySet().stream()
                .map(entry -> OrderItem.builder()
                        .order(finalOrder)
                        .productId(entry.getKey())
                        .quantity(entry.getValue())
                        .priceAtMoment(calculatePricePerUnit(entry.getKey()))
                        .build())
                .toList();
        orderItemRepository.saveAll(items);

        Map<UUID, List<OrderItem>> itemsByOrder = items.stream()
                .collect(Collectors.groupingBy(i -> i.getOrder().getId()));

        log.info("Заказ создан: orderId={}", orderId);
        return toDto(order, itemsByOrder);
    }

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
        Order order = orderRepository.findById(request.getOrderId())
                .orElseThrow(() -> new NoOrderFoundException("Заказ не найден", 400));

        List<OrderItem> items = orderItemRepository.findByOrderId(request.getOrderId());
        Map<UUID, OrderItem> itemMap = items.stream().collect(Collectors.toMap(OrderItem::getProductId, i -> i));

        long newProductPrice = 0;
        for (var entry : request.getProducts().entrySet()) {
            UUID productId = entry.getKey();
            Long returnQty = entry.getValue();
            if (returnQty <= 0) continue;

            OrderItem item = itemMap.get(productId);
            if (item == null) throw new IllegalArgumentException("Товар не в заказе");
            if (returnQty > item.getQuantity()) throw new IllegalArgumentException("Нельзя вернуть больше, чем есть");

            item.setQuantity(item.getQuantity() - returnQty);
            newProductPrice += item.getPriceAtMoment() * item.getQuantity();
        }

        order.setProductPrice(newProductPrice);
        order.setTotalPrice(newProductPrice + order.getDeliveryPrice());

        if (items.stream().allMatch(i -> i.getQuantity() == 0)) {
            // Если все товары возвращены — отменяем заказ
            statusService.transitionTo(request.getOrderId(), OrderStatus.CANCELED);
        } else {
            orderRepository.save(order);
        }
        orderItemRepository.saveAll(items);

        Map<UUID, List<OrderItem>> itemsByOrder = items.stream()
                .collect(Collectors.groupingBy(i -> order.getId()));
        return toDto(order, itemsByOrder);
    }

    @Transactional
    public OrderDto payOrder(UUID orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new NoOrderFoundException("Заказ не найден", 400));

        List<OrderItem> items = orderItemRepository.findByOrderId(order.getId());
        Map<UUID, List<OrderItem>> itemsByOrder = items.stream()
                .collect(Collectors.groupingBy(i -> order.getId()));

        OrderDto orderDtoForPayment = toDto(order, itemsByOrder);
        orderDtoForPayment.setPaymentId(order.getId());
        orderDtoForPayment.setDeliveryId(order.getId());

        // РЕАЛИЗОВАТЬ

        // var response = paymentServiceApi.createPayment(orderDtoForPayment);
        PaymentDto paymentResponse = new PaymentDto(); //response.getBody();


        //РЕАЛИЗОВАТЬ

        if (paymentResponse == null || paymentResponse.getPaymentId() == null) {
            throw new IllegalStateException("Некорректный ответ платёжного шлюза");
        }

        order.setPaymentId(paymentResponse.getPaymentId());
        order.setTotalPrice(paymentResponse.getTotalPayment());

        // Статус ставим через отдельный сервис, чтобы логика переходов была в одном месте
        statusService.transitionTo(order.getId(), OrderStatus.PAID);

        log.info("Оплата успешна: orderId={}, paymentId={}", order.getId(), order.getPaymentId());

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


    @Transactional
    public OrderDto markPaymentFailed(UUID orderId) {
        if (orderId == null) {
            throw new IllegalArgumentException("orderId обязателен");
        }

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new NoOrderFoundException("Заказ не найден: " + orderId, 404));

        statusService.transitionTo(orderId, OrderStatus.PAYMENT_FAILED);

        List<OrderItem> items = orderItemRepository.findByOrderId(orderId);
        Map<UUID, List<OrderItem>> itemsByOrder = items.stream()
                .collect(Collectors.groupingBy(i -> i.getOrder().getId()));

        log.info("Оплата заказа {} завершилась ошибкой, статус установлен: PAYMENT_FAILED", orderId);
        return toDto(order, itemsByOrder);
    }

    @Transactional
    public OrderDto markDelivered(UUID orderId) {
        if (orderId == null) {
            throw new IllegalArgumentException("orderId обязателен");
        }

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new NoOrderFoundException("Заказ не найден: " + orderId, 404));

        // Логика смены статуса инкапсулирована в OrderStatusService
        statusService.transitionTo(orderId, OrderStatus.DELIVERED);

        List<OrderItem> items = orderItemRepository.findByOrderId(orderId);
        Map<UUID, List<OrderItem>> itemsByOrder = items.stream()
                .collect(Collectors.groupingBy(i -> i.getOrder().getId()));

        log.info("Заказ {} доставлен, статус установлен: DELIVERED", orderId);
        return toDto(order, itemsByOrder);
    }

    @Transactional
    public OrderDto markDeliveryFailed(UUID orderId) {
        if (orderId == null) {
            throw new IllegalArgumentException("orderId обязателен");
        }

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new NoOrderFoundException("Заказ не найден: " + orderId, 404));

        // Логика смены статуса инкапсулирована в OrderStatusService
        statusService.transitionTo(orderId, OrderStatus.DELIVERY_FAILED);

        List<OrderItem> items = orderItemRepository.findByOrderId(orderId);
        Map<UUID, List<OrderItem>> itemsByOrder = items.stream()
                .collect(Collectors.groupingBy(i -> i.getOrder().getId()));

        log.info("Доставка заказа {} завершилась ошибкой, статус установлен: DELIVERY_FAILED", orderId);
        return toDto(order, itemsByOrder);
    }

    @Transactional
    public OrderDto markCompleted(UUID orderId) {
        if (orderId == null) {
            throw new IllegalArgumentException("orderId обязателен");
        }

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new NoOrderFoundException("Заказ не найден: " + orderId, 404));

        // Логика смены статуса инкапсулирована в OrderStatusService
        statusService.transitionTo(orderId, OrderStatus.COMPLETED);

        List<OrderItem> items = orderItemRepository.findByOrderId(orderId);
        Map<UUID, List<OrderItem>> itemsByOrder = items.stream()
                .collect(Collectors.groupingBy(i -> i.getOrder().getId()));

        log.info("Заказ {} завершён, статус установлен: COMPLETED", orderId);
        return toDto(order, itemsByOrder);
    }

    @Transactional(readOnly = true) // readOnly, потому что мы только считаем, не меняем состояние
    public OrderDto calculateTotal(UUID orderId) {
        if (orderId == null) {
            throw new IllegalArgumentException("orderId обязателен");
        }

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new NoOrderFoundException("Заказ не найден: " + orderId, 404));

        // Пересчитываем цены на основе текущих позиций в заказе
        List<OrderItem> items = orderItemRepository.findByOrderId(orderId);

        long productPrice = items.stream()
                .mapToLong(item -> item.getPriceAtMoment() * item.getQuantity())
                .sum();

        long deliveryPrice = calculateDeliveryPrice(new BookedProductsDto(
                order.getDeliveryWeight(),
                order.getDeliveryVolume(),
                order.getFragile()
        ));

        long totalPrice = productPrice + deliveryPrice;

        // Обновляем значения в сущности (если нужно хранить актуальные суммы в БД)
        order.setProductPrice(productPrice);
        order.setDeliveryPrice(deliveryPrice);
        order.setTotalPrice(totalPrice);
        // orderRepository.save(order); // Раскомментируй, если по ТЗ нужно сохранять пересчитанные суммы

        Map<UUID, List<OrderItem>> itemsByOrder = items.stream()
                .collect(Collectors.groupingBy(i -> i.getOrder().getId()));

        log.info("Пересчитана стоимость заказа {}: totalPrice={}", orderId, totalPrice);
        return toDto(order, itemsByOrder);
    }

    @Transactional(readOnly = true) // readOnly, потому что мы только считаем, не меняем состояние
    public OrderDto calculateDelivery(UUID orderId) {
        if (orderId == null) {
            throw new IllegalArgumentException("orderId обязателен");
        }

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new NoOrderFoundException("Заказ не найден: " + orderId, 404));

        List<OrderItem> items = orderItemRepository.findByOrderId(orderId);

        // Если нужно пересчитывать вес/объём по текущему составу — можно вызвать warehouseServiceApi.check(...)
        // Сейчас берём уже сохранённые значения из заказа
        long deliveryPrice = calculateDeliveryPrice(new BookedProductsDto(
                order.getDeliveryWeight(),
                order.getDeliveryVolume(),
                order.isFragile()
        ));

        // Пересчитываем productPrice на основе позиций (чтобы вернуть полный актуальный DTO)
        long productPrice = items.stream()
                .mapToLong(item -> item.getPriceAtMoment() * item.getQuantity())
                .sum();

        long totalPrice = productPrice + deliveryPrice;

        // Создаём временную копию order с пересчитанными ценами для DTO (без сохранения в БД)
        Order dtoOrder = new Order();
        dtoOrder.setId(order.getId());
        dtoOrder.setShoppingCartId(order.getShoppingCartId());
        dtoOrder.setState(order.getState());
        dtoOrder.setDeliveryWeight(order.getDeliveryWeight());
        dtoOrder.setDeliveryVolume(order.getDeliveryVolume());
        dtoOrder.setFragile(order.getFragile());
        dtoOrder.setProductPrice(productPrice);
        dtoOrder.setDeliveryPrice(deliveryPrice);
        dtoOrder.setTotalPrice(totalPrice);
        dtoOrder.setPaymentId(order.getPaymentId());
        dtoOrder.setDeliveryId(order.getDeliveryId());
        dtoOrder.setCreatedAt(order.getCreatedAt());

        Map<UUID, List<OrderItem>> itemsByOrder = items.stream()
                .collect(Collectors.groupingBy(i -> i.getOrder().getId()));

        log.info("Рассчитана стоимость доставки для заказа {}: deliveryPrice={}", orderId, deliveryPrice);
        return toDto(dtoOrder, itemsByOrder);
    }



    private OrderDto toDto(Order order, Map<UUID, List<OrderItem>> itemsByOrder) {
        List<OrderItem> currentItems = itemsByOrder.getOrDefault(order.getId(), Collections.emptyList());

        Map<UUID, Integer> productsMap = currentItems.stream()
                .filter(i -> i.getQuantity() > 0)
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

    private Long calculateProductPrice(Map<UUID, Long> products) { return 1000L; }
    private Long calculateDeliveryPrice(BookedProductsDto b) { return (long) (200 + b.getDeliveryWeight() * 5); }
    private Long calculatePricePerUnit(UUID id) { return 100L; }
}
