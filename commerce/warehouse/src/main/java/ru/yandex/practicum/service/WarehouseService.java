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
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
public class WarehouseService {

    private static final String[] ADDRESSES = {"ADDRESS_1", "ADDRESS_2"};
    private static final SecureRandom random = new SecureRandom();
    private static final String CURRENT_ADDRESS = ADDRESSES[random.nextInt(0, ADDRESSES.length)];

    private final WarehouseStockRepository warehouseStockRepository;
    private final OrderBookingRepository orderBookingRepository;

    public WarehouseService(WarehouseStockRepository warehouseStockRepository,
                            OrderBookingRepository orderBookingRepository) {
        this.warehouseStockRepository = warehouseStockRepository;
        this.orderBookingRepository = orderBookingRepository;
    }

    public BookedProductsDto checkCart(ShoppingCartDto cart) {
        Map<UUID, Long> products = cart.getProducts();
        return validateAndCalculate(products).dto;
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
        Map<UUID, Long> requiredQuantities = request.getProducts();

        ValidationResult result = validateAndCalculate(requiredQuantities);
        Map<UUID, WarehouseStock> stockMap = result.stockMap;

        List<OrderBooking> bookingsToSave = new ArrayList<>();
        List<WarehouseStock> stocksToSave = new ArrayList<>();

        for (Map.Entry<UUID, Long> entry : requiredQuantities.entrySet()) {
            UUID productId = entry.getKey();
            Long requiredQty = entry.getValue();

            WarehouseStock stock = stockMap.get(productId);
            long newQuantity = stock.getQuantity() - requiredQty;
            stock.setQuantity(newQuantity);
            stocksToSave.add(stock);

            OrderBooking booking = OrderBooking.builder()
                    .orderId(orderId)
                    .productId(productId)
                    .quantity(requiredQty)
                    .bookedAt(Instant.now())
                    .deliveryId(null)
                    .build();
            bookingsToSave.add(booking);
        }

        warehouseStockRepository.saveAll(stocksToSave);
        orderBookingRepository.saveAll(bookingsToSave);

        return result.dto;
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
    public void markOrderAsShipped(UUID orderId, UUID deliveryId) {
        if (orderId == null || deliveryId == null) {
            throw new IllegalArgumentException("orderId и deliveryId обязательны");
        }

        List<OrderBooking> bookings = orderBookingRepository.findByOrderId(orderId);

        if (bookings.isEmpty()) {
            throw new ProductInShoppingCartLowQuantityInWarehouse(
                    "Нет бронированных товаров для заказа: " + orderId,
                    "Для заказа не найдена бронь товаров на складе",
                    400,
                    null
            );
        }

            orderBookingRepository.saveAll(bookings);
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

    @Transactional
    public void returnProducts(Map<UUID, Long> products) {
        if (products == null || products.isEmpty()) {
            log.warn("Получена пустая карта возвратов");
            return;
        }

        Map<UUID, Long> validReturns = products.entrySet().stream()
                .filter(e -> e.getValue() > 0)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        if (validReturns.isEmpty()) {
            log.info("Нет валидных возвратов для обработки");
            return;
        }

        List<WarehouseStock> stocks = warehouseStockRepository.findAllByProductIdIn(validReturns.keySet());

        Map<UUID, WarehouseStock> stockMap = stocks.stream()
                .collect(Collectors.toMap(WarehouseStock::getProductId, Function.identity()));

        for (var entry : validReturns.entrySet()) {
            UUID productId = entry.getKey();
            long quantity = entry.getValue();

            WarehouseStock stock = stockMap.get(productId);
            if (stock == null) {
                throw new IllegalArgumentException("Товар не найден на складе: productId=" + productId);
            }

            long currentAvailable = stock.getQuantity();
            stock.setQuantity(currentAvailable + quantity);

            log.info("Возврат товара {}: +{} шт. Новый остаток: {}", productId, quantity, stock.getQuantity());
        }
    }


    private ValidationResult validateAndCalculate(Map<UUID, Long> products) {
        if (products.isEmpty()) {
            BookedProductsDto empty = new BookedProductsDto(0, 0, false);
            return new ValidationResult(empty, Collections.emptyMap());
        }

        List<UUID> productIds = new ArrayList<>(products.keySet());
        List<WarehouseStock> stocks = warehouseStockRepository.findByProductIdIn(productIds);
        Map<UUID, WarehouseStock> stockMap = stocks.stream()
                .collect(Collectors.toMap(WarehouseStock::getProductId, s -> s));

        double totalWeight = 0;
        double totalVolume = 0;
        boolean anyFragile = false;

        for (Map.Entry<UUID, Long> entry : products.entrySet()) {
            UUID productId = entry.getKey();
            Long requiredQty = entry.getValue();

            if (requiredQty <= 0) {
                throw new IllegalArgumentException("Quantity must be positive for product " + productId);
            }

            WarehouseStock stock = stockMap.get(productId);
            if (stock == null) {
                throw new ProductInShoppingCartLowQuantityInWarehouse(
                        "Product not found in warehouse: " + productId,
                        "Товар не найден на складе",
                        400,
                        null
                );
            }

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

            totalWeight += stock.getWeight() * requiredQty;
            totalVolume += stock.getVolume() * requiredQty;
            if (stock.isFragile()) {
                anyFragile = true;
            }
        }

        BookedProductsDto dto = new BookedProductsDto(totalWeight, totalVolume, anyFragile);
        return new ValidationResult(dto, stockMap);
    }

    private static class ValidationResult {
        final BookedProductsDto dto;
        final Map<UUID, WarehouseStock> stockMap;

        ValidationResult(BookedProductsDto dto, Map<UUID, WarehouseStock> stockMap) {
            this.dto = dto;
            this.stockMap = stockMap;
        }
    }
}
