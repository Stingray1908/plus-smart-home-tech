package ru.yandex.practicum.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import ru.yandex.practicum.dto.ProductDto;
import ru.yandex.practicum.entity.Product;
import ru.yandex.practicum.enums.ProductCategory;
import ru.yandex.practicum.mapper.ProductMapper;
import ru.yandex.practicum.repository.ProductRepository;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;

    public ProductDto createProduct(ProductDto dto) {
        Product entity = ProductMapper.toEntity(dto);
        Product saved = productRepository.save(entity);
        return ProductMapper.toDto(saved);
    }

    public Page<ProductDto> getProductsByCategory(String category, int page, int size, List<String> sort) {
        ProductCategory parsedCategory;
        try {
            parsedCategory = ProductCategory.valueOf(category.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Неверная категория. Доступные значения: LIGHTING, CONTROL, SENSORS"
            );
        }

        Sort sortObj = parseSort(sort);
        Pageable pageable = PageRequest.of(page, size, sortObj);

        Page<Product> productsPage = productRepository.findAllByProductCategory(parsedCategory, pageable);
        return productsPage.map(ProductMapper::toDto);
    }

    /**
     * Принимает список строк вида "field,asc" или "field,desc".
     * Если список пуст или null — сортирует по productName ASC.
     */
    private Sort parseSort(List<String> sorts) {

        if (sorts == null || sorts.isEmpty()) {
            return Sort.by("productName").ascending();
        }

        List<Sort.Order> orders = new ArrayList<>();

        for (String s : sorts) {
            if (s == null || s.trim().isEmpty()) {
                continue;
            }

            String[] parts = s.split(",");
            String property = parts[0].trim();
            Sort.Direction direction = Sort.Direction.ASC; // по умолчанию ASC

            if (parts.length > 1) {
                String dir = parts[1].trim().toLowerCase();
                if ("desc".equals(dir)) {
                    direction = Sort.Direction.DESC;
                } else if ("asc".equals(dir)) {
                    direction = Sort.Direction.ASC;
                } else {
                    throw new IllegalArgumentException(
                            "Неверное направление сортировки в элементе '" + s + "'. Допустимые значения: asc, desc"
                    );
                }
            }

            orders.add(new Sort.Order(direction, property));
        }

        if (orders.isEmpty()) {
            return Sort.by("productName").ascending();
        }

        return Sort.by(orders);
    }
}
