package ru.yandex.practicum.api;


import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import ru.yandex.practicum.dto.BookedProductsDto;
import ru.yandex.practicum.dto.ShoppingCartDto;

@FeignClient(name = "warehouse")
public interface WarehouseServiceApi {

    @PostMapping("/check")
    ResponseEntity<BookedProductsDto> check(@RequestBody ShoppingCartDto cart);
}
