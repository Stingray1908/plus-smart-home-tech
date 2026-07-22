package ru.yandex.practicum.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.yandex.practicum.dto.AddToCartDto;
import ru.yandex.practicum.dto.ChangeProductQuantityRequest;
import ru.yandex.practicum.dto.RemoveProductsFromCartRequest;
import ru.yandex.practicum.dto.ShoppingCartDto;
import ru.yandex.practicum.service.CartService;


import java.util.UUID;

@RestController
@RequestMapping("/api/v1/shopping-cart")
@RequiredArgsConstructor
public class ShoppingCartController {

    private final CartService cartService;

    @PutMapping
    public ResponseEntity<ShoppingCartDto> addToCart(
            @RequestParam("username") String username,
            @RequestBody AddToCartDto request
    ) {
        ShoppingCartDto dto = cartService.addToCart(username, request);
        return ResponseEntity.ok(dto);
    }

    @DeleteMapping
    public ResponseEntity<Void> deactivateCart(@RequestParam("username") String username) {
        // Проверка на пустой username уже внутри сервиса (кидает NotAuthorizedUserException)
        cartService.deactivateCart(username);
        return ResponseEntity.ok().build(); // 200 OK
    }

    @PostMapping("/remove")
    public ResponseEntity<ShoppingCartDto> removeProducts(
            @RequestParam String username,
            @RequestBody RemoveProductsFromCartRequest request) {

        ShoppingCartDto dto = cartService.removeProductsFromCart(username, request.getProductIds());
        return ResponseEntity.ok(dto);
    }

    @PostMapping("/change-quantity")
    public ResponseEntity<ShoppingCartDto> changeQuantity(
            @RequestParam("username") String username,
            @RequestBody ChangeProductQuantityRequest request) {
        ShoppingCartDto dto = cartService.changeQuantity(username, request.getProductId(), request.getNewQuantity());
        return ResponseEntity.ok(dto);
    }
}
