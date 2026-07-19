package ru.yandex.practicum.entity;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;

import ru.yandex.practicum.enums.ProductCategory;
import ru.yandex.practicum.enums.ProductState;
import ru.yandex.practicum.enums.QuantityState;

import java.util.UUID;

@Entity
@Table(name = "products")
@Getter
@Builder

public class Product {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID productId;

    @Column(nullable = false)
    private String productName;

    private String description;

    private String imageSrc;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private QuantityState quantityState;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private ProductState productState;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private ProductCategory productCategory;

    @Column(precision = 10, scale = 2)
    private Double price;
}

