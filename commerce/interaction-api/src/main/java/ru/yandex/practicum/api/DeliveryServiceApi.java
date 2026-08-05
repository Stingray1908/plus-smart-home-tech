package ru.yandex.practicum.api;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import ru.yandex.practicum.dto.ShoppingCartDto;

@FeignClient(name = "delivery", path = "/api/v1/delivery")
public interface DeliveryServiceApi {

}
