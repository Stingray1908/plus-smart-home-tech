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
    // Для UUID лучше не использовать IDENTITY: GenerationType.IDENTITY работает для числовых ID, а не UUID
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID orderId;

    // shoppingCartId можно убрать, если он не нужен для логики платежей.
    // Если нужен — оставь, но в ТЗ его нет в ответе платёжного шлюза.
    @Column
    private UUID shoppingCartId;

    // ✅ productTotal — именно так должно называться поле: сумма стоимости всех товаров
    @Column
    private Double productTotal;

    // Стоимость доставки (уже посчитанная в order-service)
    @Column
    private Double deliveryPrice;

    // НДС 10% от productTotal
    @Column
    private Double taxAmount;

    // Итоговая сумма: productTotal + taxAmount + deliveryPrice
    @Column(nullable = false)
    private Double totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private PaymentStatus status;

    @Column(updatable = false)
    private LocalDateTime createdAt;

    @Column
    private LocalDateTime updatedAt;
}
