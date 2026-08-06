package ru.yandex.practicum.api;

import org.springframework.stereotype.Component;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import ru.yandex.practicum.dto.*;

import java.util.Map;
import java.util.UUID;

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

    @Override
    public ResponseEntity<AddressDto> getAddress() {
        return null;
    }

    @Override
    public ResponseEntity<BookedProductsDto> assembleOrder(AssemblyProductsForOrderRequest request) {
        return null;
    }

    @Override
    public ResponseEntity<Void> markOrderShipped(ShippedToDeliveryRequest request) {
        return null;
    }

    @Override
    public ResponseEntity<Void> returnProducts(Map<UUID, Long> products) {
        return null;
    }
}
