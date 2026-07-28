package ru.yandex.practicum.entity;

import jakarta.persistence.*;
import lombok.*;
import ru.yandex.practicum.enums.ProductCategory;
import ru.yandex.practicum.enums.ProductState;
import ru.yandex.practicum.enums.QuantityState;

import java.util.UUID;

@Entity
@Table(name = "products")
@Setter
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor

public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
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

    private Double price;
}

