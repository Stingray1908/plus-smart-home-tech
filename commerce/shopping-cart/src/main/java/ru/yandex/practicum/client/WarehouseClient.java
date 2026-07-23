package ru.yandex.practicum.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import ru.yandex.practicum.dto.BookedProductsDto;
import ru.yandex.practicum.dto.ShoppingCartDto;

@FeignClient(name = "warehouse-app", url = "http://localhost:8082")
public interface WarehouseClient {

    @PostMapping("/api/v1/warehouse/check")
    BookedProductsDto check(@RequestBody ShoppingCartDto cart);
}
