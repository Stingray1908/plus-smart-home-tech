package ru.yandex.practicum.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.yandex.practicum.api.WarehouseServiceApi;

import ru.yandex.practicum.dto.*;
import ru.yandex.practicum.service.WarehouseService;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/warehouse")
@RequiredArgsConstructor
public class WarehouseController implements WarehouseServiceApi {

    private final WarehouseService warehouseService;

    //
    @Override
    public ResponseEntity<BookedProductsDto> check(@RequestBody ShoppingCartDto cart) {
        BookedProductsDto result = warehouseService.checkCart(cart);
        return ResponseEntity.ok(result);
    }

    //
    @PutMapping
    public ResponseEntity<Void> addProduct(@RequestBody NewProductInWarehouseRequest request) {
        warehouseService.addProductToWarehouse(request);
        return ResponseEntity.ok().build();
    }

    //
    @Override
    public ResponseEntity<AddressDto> getAddress() {
        AddressDto address = warehouseService.getWarehouseAddress();
        return ResponseEntity.ok(address);
    }

    //
    @Override
    public ResponseEntity<Void> addQuantity(@RequestBody AddProductToWarehouseRequest request) {
        warehouseService.addQuantityToWarehouse(request.getProductId(), request.getQuantity());
        return ResponseEntity.ok().build();
    }


    /**
     * Создает бронь на складе
     * @param request
     * @return
     */
    @Override
    public ResponseEntity<BookedProductsDto> assembleOrder(@RequestBody AssemblyProductsForOrderRequest request) {
        return ResponseEntity.ok(warehouseService.assembleOrder(request));
    }

    
    @Override
    public ResponseEntity<Void> markOrderShipped(@RequestBody ShippedToDeliveryRequest request) {
        warehouseService.markOrderAsShipped(request.getOrderId(), request.getDeliveryId());
        return ResponseEntity.ok().build();
    }

    //
    @Override
    public ResponseEntity<Void> returnProducts(@RequestBody Map<UUID, Long> products) {
        if (products == null || products.isEmpty()) {
            // Можно вернуть 400, если пустой возврат невалиден
            return ResponseEntity.badRequest().build();
        }

        warehouseService.returnProducts(products);
        return ResponseEntity.ok().build();
    }
}
