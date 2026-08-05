package ru.yandex.practicum.api;

import org.springframework.stereotype.Component;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import ru.yandex.practicum.dto.AddProductToWarehouseRequest;
import ru.yandex.practicum.dto.BookedProductsDto;
import ru.yandex.practicum.dto.ShoppingCartDto;

@Component
public class WarehouseServiceApiFallback implements WarehouseServiceApi {

    @Override
    public ResponseEntity<BookedProductsDto> check(@RequestBody ShoppingCartDto cart) {
        return ResponseEntity.status(503).build();
    }

    @Override
    public ResponseEntity<Void> addQuantity(@RequestBody AddProductToWarehouseRequest request) {
        return ResponseEntity.status(503).build();
    }
}
