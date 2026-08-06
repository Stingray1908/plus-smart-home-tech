package ru.yandex.practicum.api;


import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import ru.yandex.practicum.dto.*;

@FeignClient(name = "warehouse", path = "/api/v1/warehouse/", fallback = WarehouseServiceApiFallback.class)
public interface WarehouseServiceApi {

    @PostMapping("/check")
    ResponseEntity<BookedProductsDto> check(@RequestBody ShoppingCartDto cart);

    @PostMapping("/add")
    public ResponseEntity<Void> addQuantity(@RequestBody AddProductToWarehouseRequest request);

    @GetMapping("/address")
    public ResponseEntity<AddressDto> getAddress();

    @PostMapping("/assembly")
    ResponseEntity<BookedProductsDto> assembleOrder(@RequestBody AssemblyProductsForOrderRequest request);

    @PostMapping("/shipped")
    ResponseEntity<Void> markOrderShipped(@RequestBody ShippedToDeliveryRequest request);
}
