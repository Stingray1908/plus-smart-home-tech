package ru.yandex.practicum.entity;

import jakarta.persistence.*;
import lombok.*;


import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "payments")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private UUID id;

    @Column(nullable = false)
    private UUID orderId;

    @Column
    private UUID shoppingCartId;

    @Column(precision = 19, scale = 2)
    private Double productPrice;

    @Column(precision = 19, scale = 2)
    private Double deliveryPrice;

    @Column(precision = 19, scale = 2)
    private Double taxAmount;

    @Column(precision = 19, scale = 2, nullable = false)
    private Double totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private PaymentStatus status;

    @Column(updatable = false)
    private LocalDateTime createdAt;

    @Column
    private LocalDateTime updatedAt;
}
