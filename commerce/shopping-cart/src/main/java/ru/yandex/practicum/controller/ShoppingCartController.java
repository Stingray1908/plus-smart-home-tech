package ru.yandex.practicum.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.yandex.practicum.dto.AddToCartDto;
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
}
