package ru.yandex.practicum.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "order_bookings")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderBooking {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private UUID orderId;

    @Column(nullable = false)
    private UUID productId;

    @Column(nullable = false)
    private long quantity;

    @Column(nullable = false, columnDefinition = "TIMESTAMP")
    private Instant bookedAt;

    private UUID deliveryId;
}
