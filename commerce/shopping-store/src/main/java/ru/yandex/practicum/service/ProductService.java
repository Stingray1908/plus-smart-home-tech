package ru.yandex.practicum.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.yandex.practicum.dto.ProductDto;
import ru.yandex.practicum.entity.Product;
import ru.yandex.practicum.repository.ProductRepository;

import static ru.yandex.practicum.mapper.ProductMapper.toDto;
import static ru.yandex.practicum.mapper.ProductMapper.toEntity;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository repository;

    public ProductDto createProduct(ProductDto dto) {

        Product entity = toEntity(dto);
        Product saved = repository.save(entity);

        return toDto(saved);
    }
}
