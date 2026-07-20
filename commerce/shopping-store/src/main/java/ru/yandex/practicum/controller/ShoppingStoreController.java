package ru.yandex.practicum.controller;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.yandex.practicum.dto.ProductDto;
import ru.yandex.practicum.exception.ProductNotFoundException;
import ru.yandex.practicum.service.ProductService;

import java.util.List;
import java.util.UUID;

import static ru.yandex.practicum.mapper.ProductMapper.toDto;

@RestController
@RequestMapping("/api/v1/shopping-store")
@RequiredArgsConstructor
public class ShoppingStoreController {

    private final ProductService productService;

    @PutMapping
    public ResponseEntity<ProductDto> createProduct(@RequestBody ProductDto dto) {
        ProductDto saved = productService.createProduct(dto);
        return ResponseEntity.ok(saved);
    }

    @GetMapping
    public ResponseEntity<Page<ProductDto>> getProductsByCategory(
            @RequestParam String category,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) List<String> sort
    ) {
        Page<ProductDto> result = productService.getProductsByCategory(category, page, size, sort);
        return ResponseEntity.ok(result);
    }


    @GetMapping("/{productId}")
    public ResponseEntity<ProductDto> getProductById(@PathVariable UUID productId) {
        ProductDto dto = productService.findById(productId);
        return ResponseEntity.ok(dto);
    }
}
