package ru.yandex.practicum.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.yandex.practicum.dto.AddressDto;
import ru.yandex.practicum.dto.NewProductInWarehouseRequest;
import ru.yandex.practicum.dto.BookedProductsDto;
import ru.yandex.practicum.dto.ShoppingCartDto;
import ru.yandex.practicum.service.WarehouseService;
import ru.yandex.practicum.api.WarehouseServiceApi;

@RestController
@RequestMapping("/api/v1/warehouse")
@RequiredArgsConstructor
public class WarehouseCheckController implements WarehouseServiceApi{

    private final WarehouseService warehouseService;

    @Override
    public ResponseEntity<BookedProductsDto> check(ShoppingCartDto cart) {
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
