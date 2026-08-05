package ru.yandex.practicum.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.yandex.practicum.dto.DeliveryState;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "deliveries")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Delivery {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO) // или IDENTITY, если у тебя PostgreSQL/MySQL
    private UUID id;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    // Вложенные адреса храним как отдельные колонки или JSON — здесь вариант с отдельными полями
    @Column(name = "from_country")
    private String fromCountry;
    @Column(name = "from_city")
    private String fromCity;
    @Column(name = "from_street")
    private String fromStreet;
    @Column(name = "from_house")
    private String fromHouse;
    @Column(name = "from_flat")
    private String fromFlat;

    @Column(name = "to_country")
    private String toCountry;
    @Column(name = "to_city")
    private String toCity;
    @Column(name = "to_street")
    private String toStreet;
    @Column(name = "to_house")
    private String toHouse;
    @Column(name = "to_flat")
    private String toFlat;

    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_state", nullable = false)
    @Builder.Default
    private DeliveryState deliveryState = DeliveryState.CREATED;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
