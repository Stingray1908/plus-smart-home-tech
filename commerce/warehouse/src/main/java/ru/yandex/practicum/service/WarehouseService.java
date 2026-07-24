package ru.yandex.practicum.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.yandex.practicum.dto.AddressDto;
import ru.yandex.practicum.dto.BookedProductsDto;
import ru.yandex.practicum.dto.NewProductInWarehouseRequest;
import ru.yandex.practicum.dto.ShoppingCartDto;
import ru.yandex.practicum.entity.WarehouseStock;
import ru.yandex.practicum.exception.ProductInShoppingCartLowQuantityInWarehouse;
import ru.yandex.practicum.exception.SpecifiedProductAlreadyInWarehouseException;
import ru.yandex.practicum.repository.WarehouseStockRepository;

import java.security.SecureRandom;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class WarehouseService {

    private static final String[] ADDRESSES = {"ADDRESS_1", "ADDRESS_2"};
    private static final SecureRandom random = new SecureRandom();
    private static final String CURRENT_ADDRESS = ADDRESSES[random.nextInt(0, ADDRESSES.length)];

    private final WarehouseStockRepository warehouseStockRepository;

    public WarehouseService(WarehouseStockRepository warehouseStockRepository) {
        this.warehouseStockRepository = warehouseStockRepository;
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
}
