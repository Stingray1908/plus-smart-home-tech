package ru.yandex.practicum.api;

import org.springframework.cloud.openfeign.FeignClient;

@FeignClient(name = "shopping-store", path = "/api/v1/shopping-store")
public interface StoreServiceApi {
}
