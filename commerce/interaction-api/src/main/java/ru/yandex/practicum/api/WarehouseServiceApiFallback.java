package ru.yandex.practicum.api;

import org.springframework.stereotype.Component;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import ru.yandex.practicum.dto.BookedProductsDto;
import ru.yandex.practicum.dto.ShoppingCartDto;

@Component
public class WarehouseServiceApiFallback implements WarehouseServiceApi {

    @Override
    public ResponseEntity<BookedProductsDto> check(@RequestBody ShoppingCartDto cart) {
        BookedProductsDto fallbackResult = new BookedProductsDto();
        fallbackResult.setDeliveryWeight(-1.0);
        fallbackResult.setDeliveryVolume(-1.0);
        fallbackResult.setFragile(false);

        return ResponseEntity.ok(fallbackResult);
    }
}
