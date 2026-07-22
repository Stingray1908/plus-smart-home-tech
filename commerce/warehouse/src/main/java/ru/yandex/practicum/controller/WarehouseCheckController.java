package ru.yandex.practicum.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.yandex.practicum.dto.BookedProductsDto;
import ru.yandex.practicum.dto.ShoppingCartDto;
import ru.yandex.practicum.service.WarehouseService;

@RestController
@RequestMapping("/api/v1/warehouse")
@RequiredArgsConstructor
public class WarehouseCheckController {

    private final WarehouseService warehouseCheckService;

    @PostMapping("/check")
    public ResponseEntity<BookedProductsDto> check(
            @RequestBody ShoppingCartDto cart
    ) {
        BookedProductsDto result = warehouseCheckService.checkCart(cart);
        return ResponseEntity.ok(result);
    }


}
