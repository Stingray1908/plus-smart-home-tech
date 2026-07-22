package ru.yandex.practicum.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.yandex.practicum.dto.BookedProductsDto;
import ru.yandex.practicum.dto.NewProductInWarehouseRequest;
import ru.yandex.practicum.dto.ShoppingCartDto;
import ru.yandex.practicum.entity.WarehouseStock;
import ru.yandex.practicum.exception.ProductInShoppingCartLowQuantityInWarehouse;
import ru.yandex.practicum.exception.SpecifiedProductAlreadyInWarehouseException;
import ru.yandex.practicum.repository.WarehouseStockRepository;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class WarehouseService {

    private final WarehouseStockRepository warehouseStockRepository;

    public WarehouseService(WarehouseStockRepository warehouseStockRepository) {
        this.warehouseStockRepository = warehouseStockRepository;
    }

    public BookedProductsDto checkCart(ShoppingCartDto cart) {
        // 1. Собираем все ID товаров из корзины в список
        List<UUID> productIds = new ArrayList<>(cart.getProducts().keySet());

        if (productIds.isEmpty()) {
            return BookedProductsDto.builder()
                    .deliveryWeight(0)
                    .deliveryVolume(0)
                    .fragile(false)
                    .build();
        }

        // 2. Один запрос ко всей партии товаров
        List<WarehouseStock> stocks = warehouseStockRepository.findByProductIdIn(productIds);

        // 3. Превращаем список в Map<UUID, WarehouseStock> для быстрого поиска
        Map<UUID, WarehouseStock> stockMap = stocks.stream()
                .collect(Collectors.toMap(
                        WarehouseStock::getProductId,
                        stock -> stock
                ));

        double totalWeight = 0;
        double totalVolume = 0;
        boolean anyFragile = false;

        // 4. Проходим по корзине и проверяем данные уже из Map (это очень быстро)
        for (Map.Entry<UUID, Long> entry : cart.getProducts().entrySet()) {
            UUID productId = entry.getKey();
            Long requiredQty = entry.getValue();

            // Пытаемся найти запись в Map
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

        // 1. Проверяем, есть ли уже такой товар на складе
        Optional<WarehouseStock> existing = warehouseStockRepository.findByProductId(productId);
        if (existing.isPresent()) {
            throw new SpecifiedProductAlreadyInWarehouseException(
                    "Product with id " + productId + " is already registered in warehouse",
                    "Товар с таким идентификатором уже зарегистрирован на складе",
                    400,
                    null
            );
        }

        // 2. Считаем объём: width * height * depth
        double volume = 0;
        if (request.getDimension() != null) {
            volume = request.getDimension().getWidth() *
                    request.getDimension().getHeight() *
                    request.getDimension().getDepth();
        }

        // 3. Создаём новую запись
        WarehouseStock stock = WarehouseStock.builder()
                .productId(productId)
                .fragile(request.isFragile())
                .weight(request.getWeight())
                .volume(volume)
                .quantity(0L) // По умолчанию 0, потом кто-то другой (или этот же метод) добавит количество
                .build();

        warehouseStockRepository.save(stock);
    }

}
