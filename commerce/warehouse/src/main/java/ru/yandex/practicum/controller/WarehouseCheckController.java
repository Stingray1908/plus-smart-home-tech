package ru.yandex.practicum.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.yandex.practicum.AddressDto;
import ru.yandex.practicum.BookedProductsDto;
import ru.yandex.practicum.NewProductInWarehouseRequest;
import ru.yandex.practicum.ShoppingCartDto;
import ru.yandex.practicum.service.WarehouseService;
import ru.yandex.practicum.store.api.WarehouseServiceApi;

@RestController
@RequestMapping("/api/v1/warehouse")
@RequiredArgsConstructor
public class WarehouseCheckController {

    private final WarehouseService warehouseService;

    @PostMapping("/check")
    public ResponseEntity<BookedProductsDto> check(@RequestBody ShoppingCartDto cart) {
        BookedProductsDto result = warehouseService.checkCart(cart);
        return ResponseEntity.ok(result);
    }

    @PutMapping
    public ResponseEntity<Void> addProduct(@RequestBody NewProductInWarehouseRequest request) {
        warehouseService.addProductToWarehouse(request);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/address")
    public ResponseEntity<AddressDto> getAddress() {
        AddressDto address = warehouseService.getWarehouseAddress();
        return ResponseEntity.ok(address);
    }
}
