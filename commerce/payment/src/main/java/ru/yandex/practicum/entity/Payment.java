package ru.yandex.practicum.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payments")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Payment {

    @Id
    @Column(name = "payment_id", nullable = false, unique = true)
    private UUID paymentId;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    // Изменили с Long на Double, чтобы соответствовать ProductDto.price
    private Double productTotal;
    private Double deliveryTotal;
    private Double feeTotal;
    private Double totalPayment;

    @Enumerated(EnumType.STRING)
    private PaymentStatus status;

    private Instant createdAt;
    private Instant paidAt;
}
