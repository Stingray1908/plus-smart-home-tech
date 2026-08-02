package ru.yandex.practicum.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.yandex.practicum.api.CartServiceApi;
import ru.yandex.practicum.dto.ChangeProductQuantityRequest;
import ru.yandex.practicum.dto.ShoppingCartDto;
import ru.yandex.practicum.service.CartService;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/shopping-cart")
@RequiredArgsConstructor
public class ShoppingCartController implements CartServiceApi {

    private final CartService cartService;

    @PutMapping
    public ResponseEntity<ShoppingCartDto> addToCart(
            @RequestParam("username") String username,
            @RequestBody Map<UUID, Long> products
    ) {
        ShoppingCartDto dto = cartService.addToCart(username, products);
        return ResponseEntity.ok(dto);
    }

    @DeleteMapping
    public ResponseEntity<Void> deactivateCart(@RequestParam("username") String username) {
        cartService.deactivateCart(username);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/remove")
    public ResponseEntity<ShoppingCartDto> removeProducts(
            @RequestParam String username,
            @RequestBody List<UUID> request) {

        ShoppingCartDto dto = cartService.removeProductsFromCart(username, request);
        return ResponseEntity.ok(dto);
    }

    @PostMapping("/change-quantity")
    public ResponseEntity<ShoppingCartDto> changeQuantity(
            @RequestParam("username") String username,
            @RequestBody ChangeProductQuantityRequest request) {
        ShoppingCartDto dto = cartService.changeQuantity(username, request.getProductId(), request.getNewQuantity());
        return ResponseEntity.ok(dto);
    }

    @Override
    public ResponseEntity<ShoppingCartDto> getShoppingCart(
            @RequestParam("username") String username) {

        ShoppingCartDto dto = cartService.getShoppingCart(username);
        return ResponseEntity.ok(dto);
    }
}
