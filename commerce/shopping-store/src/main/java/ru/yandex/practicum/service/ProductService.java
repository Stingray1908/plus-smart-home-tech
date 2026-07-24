package ru.yandex.practicum.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.stereotype.Service;
import ru.yandex.practicum.dto.ProductDto;
import ru.yandex.practicum.entity.Product;
import ru.yandex.practicum.enums.ProductCategory;
import ru.yandex.practicum.enums.QuantityState;
import ru.yandex.practicum.exception.ProductNotFoundException;
import ru.yandex.practicum.mapper.ProductMapper;
import ru.yandex.practicum.repository.ProductRepository;

import java.util.UUID;

import static ru.yandex.practicum.mapper.ProductMapper.toDto;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;

    public ProductDto createProduct(ProductDto dto) {
        Product entity = ProductMapper.toEntity(dto);
        Product saved = productRepository.save(entity);
        return toDto(saved);
    }

    public Page<ProductDto> getProductsByCategory(String category, int page, int size, String sort) {
        ProductCategory parsedCategory;
        try {
            parsedCategory = ProductCategory.valueOf(category.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Неверная категория. Доступные значения: LIGHTING, CONTROL, SENSORS"
            );
        }

        Sort sortObj = parseSort(sort); // теперь sort — это строка "productName,DESC" или null
        Pageable pageable = PageRequest.of(page, size, sortObj);

        Page<Product> productsPage = productRepository.findAllByProductCategory(parsedCategory, pageable);
        return productsPage.map(ProductMapper::toDto);
    }

    public ProductDto findById(UUID productId) {
        return toDto(findByIdOrThrowNotFound(productId));
    }

    public ProductDto updateProduct(ProductDto dto) {
        UUID productId = dto.getProductId();
        if (productId == null) {
            throw new IllegalArgumentException("productId is required");
        }

        Product product = findByIdOrThrowNotFound(productId);
        Product saved = productRepository.save(updateProduct(product, dto));

        return toDto(saved);
    }

    public boolean deactivateProduct(UUID productId) {
        Product product = findByIdOrThrowNotFound(productId);
        product.setProductState(ru.yandex.practicum.enums.ProductState.DEACTIVATE);
        productRepository.save(product);
        return true;
    }

    public boolean setQuantityState(UUID productId, QuantityState quantityState) {
        Product product = findByIdOrThrowNotFound(productId);
        product.setQuantityState(quantityState);
        productRepository.save(product);
        return true;
    }

    private Sort parseSort(String sortParam) {
        if (sortParam == null || sortParam.isBlank()) {
            return Sort.unsorted();
        }

        String[] parts = sortParam.split(",", 2);
        if (parts.length != 2) {
            return Sort.unsorted();
        }

        String field = parts[0].trim();
        String directionStr = parts[1].trim().toUpperCase();

        Direction direction = Direction.ASC;
        if ("DESC".equals(directionStr)) {
            direction = Direction.DESC;
        }

        return Sort.by(new Sort.Order(direction, field));
    }

    private Product findByIdOrThrowNotFound(UUID productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(
                        "Product not found with id: " + productId,
                        "Товар с таким идентификатором не найден",
                        404,
                        null
                ));
    }

    private Product updateProduct(Product exist, ProductDto update) {
        if (update.getProductName() != null) {
            exist.setProductName(update.getProductName());
        }
        if (update.getDescription() != null) {
            exist.setDescription(update.getDescription());
        }
        if (update.getImageSrc() != null) {
            exist.setImageSrc(update.getImageSrc());
        }
        if (update.getQuantityState() != null) {
            exist.setQuantityState(update.getQuantityState());
        }
        if (update.getProductState() != null) {
            exist.setProductState(update.getProductState());
        }
        if (update.getProductCategory() != null) {
            exist.setProductCategory(update.getProductCategory());
        }
        if (update.getPrice() != null) {
            exist.setPrice(update.getPrice());
        }
        return exist;
    }
}
