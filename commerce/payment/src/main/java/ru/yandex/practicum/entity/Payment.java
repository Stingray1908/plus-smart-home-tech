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
    private Long id;

    @Column(nullable = false)
    private UUID orderId;

    @Column
    private UUID shoppingCartId;

    @Column(precision = 19, scale = 2)
    private BigDecimal productPrice;

    @Column(precision = 19, scale = 2)
    private BigDecimal deliveryPrice;

    @Column(precision = 19, scale = 2)
    private BigDecimal taxAmount;

    @Column(precision = 19, scale = 2, nullable = false)
    private BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private PaymentStatus status;

    @Column(updatable = false)
    private LocalDateTime createdAt;

    @Column
    private LocalDateTime updatedAt;
}
