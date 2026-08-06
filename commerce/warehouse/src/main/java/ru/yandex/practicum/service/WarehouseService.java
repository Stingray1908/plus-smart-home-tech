package ru.yandex.practicum.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.yandex.practicum.dto.*;
import ru.yandex.practicum.entity.OrderBooking;
import ru.yandex.practicum.entity.WarehouseStock;
import ru.yandex.practicum.exception.ProductInShoppingCartLowQuantityInWarehouse;
import ru.yandex.practicum.exception.SpecifiedProductAlreadyInWarehouseException;
import ru.yandex.practicum.repository.OrderBookingRepository;
import ru.yandex.practicum.repository.WarehouseStockRepository;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class WarehouseService {

    private static final String[] ADDRESSES = {"ADDRESS_1", "ADDRESS_2"};
    private static final SecureRandom random = new SecureRandom();
    private static final String CURRENT_ADDRESS = ADDRESSES[random.nextInt(0, ADDRESSES.length)];

    private final WarehouseStockRepository warehouseStockRepository;
    private final OrderBookingRepository orderBookingRepository;

    public WarehouseService(WarehouseStockRepository warehouseStockRepository, OrderBookingRepository orderBookingRepository) {
        this.warehouseStockRepository = warehouseStockRepository;
        this.orderBookingRepository = orderBookingRepository;
    }

    public BookedProductsDto checkCart(ShoppingCartDto cart) {
        List<UUID> productIds = new ArrayList<>(cart.getProducts().keySet());

        if (productIds.isEmpty()) {
            return BookedProductsDto.builder()
                    .deliveryWeight(0)
                    .deliveryVolume(0)
                    .fragile(false)
                    .build();
        }

        List<WarehouseStock> stocks = warehouseStockRepository.findByProductIdIn(productIds);

        Map<UUID, WarehouseStock> stockMap = stocks.stream()
                .collect(Collectors.toMap(
                        WarehouseStock::getProductId,
                        stock -> stock
                ));

        double totalWeight = 0;
        double totalVolume = 0;
        boolean anyFragile = false;

        for (Map.Entry<UUID, Long> entry : cart.getProducts().entrySet()) {
            UUID productId = entry.getKey();
            Long requiredQty = entry.getValue();

            WarehouseStock stock = stockMap.get(productId);

            if (stock == null) {
                throw new ProductInShoppingCartLowQuantityInWarehouse(
                        "Product not found in warehouse: " + productId,
                        "Товар не найден на складе",
                        400,
                        null);
            }

            if (stock.getQuantity() < requiredQty) {
                throw new ProductInShoppingCartLowQuantityInWarehouse(
                        "Not enough quantity for product " + productId +
                                ": required " + requiredQty + ", available " + stock.getQuantity(),
                        "Недостаточно товара на складе: требуется " + requiredQty +
                                ", доступно " + stock.getQuantity(),
                        400,
                        null);
            }

            totalWeight += stock.getWeight() * requiredQty;
            totalVolume += stock.getVolume() * requiredQty;
            if (stock.isFragile()) {
                anyFragile = true;
            }
        }

        return BookedProductsDto.builder()
                .deliveryWeight(totalWeight)
                .deliveryVolume(totalVolume)
                .fragile(anyFragile)
                .build();
    }

    @Transactional
    public void addProductToWarehouse(NewProductInWarehouseRequest request) {
        UUID productId = request.getProductId();

        Optional<WarehouseStock> existing = warehouseStockRepository.findByProductId(productId);
        if (existing.isPresent()) {
            throw new SpecifiedProductAlreadyInWarehouseException(
                    "Product with id " + productId + " is already registered in warehouse",
                    "Товар с таким идентификатором уже зарегистрирован на складе",
                    400,
                    null
            );
        }

        double volume = 0;
        if (request.getDimension() != null) {
            volume = request.getDimension().getWidth() *
                    request.getDimension().getHeight() *
                    request.getDimension().getDepth();
        }

        WarehouseStock stock = WarehouseStock.builder()
                .productId(productId)
                .fragile(request.isFragile())
                .weight(request.getWeight())
                .volume(volume)
                .quantity(0L)
                .build();

        warehouseStockRepository.save(stock);
    }

    @Transactional
    public BookedProductsDto assembleOrder(AssemblyProductsForOrderRequest request) {
        if (request.getOrderId() == null) {
            throw new IllegalArgumentException("orderId is required");
        }
        if (request.getProducts() == null || request.getProducts().isEmpty()) {
            throw new IllegalArgumentException("products map must be non-empty");
        }

        UUID orderId = request.getOrderId();
        Map<UUID, Integer> requiredQuantities = request.getProducts();
        List<UUID> productIds = new ArrayList<>(requiredQuantities.keySet());

        // Получаем текущие остатки
        List<WarehouseStock> stocks = warehouseStockRepository.findByProductIdIn(productIds);
        Map<UUID, WarehouseStock> stockMap = stocks.stream()
                .collect(Collectors.toMap(WarehouseStock::getProductId, stock -> stock));

        double totalWeight = 0;
        double totalVolume = 0;
        boolean anyFragile = false;

        List<OrderBooking> bookingsToSave = new ArrayList<>();

        for (Map.Entry<UUID, Integer> entry : requiredQuantities.entrySet()) {
            UUID productId = entry.getKey();
            int requiredQty = entry.getValue();

            if (requiredQty <= 0) {
                throw new IllegalArgumentException("Quantity must be positive for product " + productId);
            }

            WarehouseStock stock = stockMap.get(productId);
            if (stock == null) {
                throw new ProductInShoppingCartLowQuantityInWarehouse(
                        "Product not found in warehouse: " + productId,
                        "Товар не найден на складе: " + productId,
                        400,
                        null
                );
            }

            // Сравниваем Integer с Long: приводим к long
            if (stock.getQuantity() < requiredQty) {
                throw new ProductInShoppingCartLowQuantityInWarehouse(
                        "Not enough quantity for product " + productId +
                                ": required " + requiredQty + ", available " + stock.getQuantity(),
                        "Недостаточно товара на складе: требуется " + requiredQty +
                                ", доступно " + stock.getQuantity(),
                        400,
                        null
                );
            }

            // Уменьшаем остаток
            long newQuantity = stock.getQuantity() - requiredQty;
            stock.setQuantity(newQuantity);
            // save внутри цикла допустим, потому что весь метод @Transactional: при ошибке всё откатится
            warehouseStockRepository.save(stock);

            // Создаём бронь
            OrderBooking booking = OrderBooking.builder()
                    .orderId(orderId)
                    .productId(productId)
                    .quantity(requiredQty)
                    .bookedAt(Instant.now())
                    .deliveryId(null) // пока не передан в доставку
                    .build();
            bookingsToSave.add(booking);

            totalWeight += stock.getWeight() * requiredQty;
            totalVolume += stock.getVolume() * requiredQty;
            if (stock.isFragile()) {
                anyFragile = true;
            }
        }

        orderBookingRepository.saveAll(bookingsToSave);

        return BookedProductsDto.builder()
                .deliveryWeight(totalWeight)
                .deliveryVolume(totalVolume)
                .fragile(anyFragile)
                .build();
    }

    @Transactional
    public void markOrderAsShipped(UUID orderId, UUID deliveryId) {
        if (orderId == null || deliveryId == null) {
            throw new IllegalArgumentException("orderId и deliveryId обязательны");
        }

        // Находим все брони по заказу
        List<OrderBooking> bookings = orderBookingRepository.findByOrderId(orderId);

        if (bookings.isEmpty()) {
            // Если брони нет, значит заказ не собирали — можно либо вернуть ошибку, либо ничего не делать.
            // Для учебной задачи логично кинуть ошибку, чтобы не «проглатывать» странные запросы.
            throw new ProductInShoppingCartLowQuantityInWarehouse(
                    "Нет бронированных товаров для заказа: " + orderId,
                    "Для заказа не найдена бронь товаров на складе",
                    400,
                    null
            );
        }

        for (OrderBooking booking : bookings) {
            booking.setDeliveryId(deliveryId);
            // save можно вызывать в цикле: всё равно всё в одной транзакции
            orderBookingRepository.save(booking);
        }
    }

    public AddressDto getWarehouseAddress() {
        return AddressDto.builder()
                .country(CURRENT_ADDRESS)
                .city(CURRENT_ADDRESS)
                .street(CURRENT_ADDRESS)
                .house(CURRENT_ADDRESS)
                .flat(CURRENT_ADDRESS)
                .build();
    }

    @Transactional
    public void addQuantityToWarehouse(UUID productId, Long quantity) {
        if (quantity == null || quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }

        WarehouseStock stock = warehouseStockRepository.findByProductId(productId)
                .orElseThrow(() -> new ProductInShoppingCartLowQuantityInWarehouse(
                        "Product not found in warehouse: " + productId,
                        "Нет информации о товаре на складе",
                        400,
                        null
                ));

        stock.setQuantity(stock.getQuantity() + quantity);
        warehouseStockRepository.save(stock);
    }

    /**
     * Обрабатывает возврат: увеличивает остатки по каждому productId на указанное количество.
     *
     * @param products productId -> quantity
     */
    @Transactional
    public void returnProducts(Map<UUID, Long> products) {
        if (products == null || products.isEmpty()) {
            log.warn("Получена пустая карта возвратов");
            return;
        }

        for (Map.Entry<UUID, Long> entry : products.entrySet()) {
            UUID productId = entry.getKey();
            long quantity = entry.getValue();

            if (quantity <= 0) {
                log.warn("Пропущен товар {}: количество возврата {} <= 0", productId, quantity);
                continue;
            }

            WarehouseStock stock = warehouseStockRepository.findByProductId(productId)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Товар не найден на складе: productId=" + productId));

            long currentAvailable = stock.getQuantity();
            stock.setQuantity(currentAvailable + quantity);

            log.info("Возврат товара {}: +{} шт. Новый остаток: {}", productId, quantity, stock.getQuantity());
        }
    }
}
