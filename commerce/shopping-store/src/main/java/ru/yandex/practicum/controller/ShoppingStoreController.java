package ru.yandex.practicum.controller;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.yandex.practicum.api.StoreServiceApi;
import ru.yandex.practicum.dto.ProductDto;
import ru.yandex.practicum.service.ProductService;
import ru.yandex.practicum.dto.SetProductQuantityStateRequest;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/shopping-store")
@RequiredArgsConstructor
public class ShoppingStoreController implements StoreServiceApi {

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

    @PostMapping
    public ResponseEntity<ProductDto> updateProduct(@RequestBody ProductDto dto) {
        ProductDto updated = productService.updateProduct(dto);
        return ResponseEntity.ok(updated);
    }

    @PostMapping("/removeProductFromStore")
    public ResponseEntity<Boolean> removeProductFromStore(@RequestParam UUID productId) {
        boolean result = productService.deactivateProduct(productId);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/quantityState")
    public ResponseEntity<Boolean> setQuantityState(@RequestBody SetProductQuantityStateRequest request) {
        boolean result = productService.setQuantityState(request.getProductId(), request.getQuantityState());
        return ResponseEntity.ok(result);
    }

}
