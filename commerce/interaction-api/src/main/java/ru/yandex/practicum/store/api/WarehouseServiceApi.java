package ru.yandex.practicum.store.api;


import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import ru.yandex.practicum.cart.dto.BookedProductsDto;
import ru.yandex.practicum.cart.dto.ShoppingCartDto;

@FeignClient(name = "warehouse")
public interface WarehouseServiceApi {

    @PostMapping("/api/v1/warehouse/check")
    ResponseEntity<BookedProductsDto> check(@RequestBody ShoppingCartDto cart);
}
