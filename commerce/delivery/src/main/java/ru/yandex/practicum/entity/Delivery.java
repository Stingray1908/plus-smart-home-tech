package ru.yandex.practicum.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.GenericGenerator;
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
    // Hibernate сам возьмет UUID из БД (PostgreSQL uuid_generate_v4 или аналог)
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    private UUID id;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    // --- Адреса ---
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

    // --- Параметры груза (заполняются позже) ---
    @Column(name = "total_weight")
    private Double totalWeight;

    @Column(name = "total_volume")
    private Double totalVolume;

    @Column(name = "is_fragile")
    private Boolean isFragile;

    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_state", nullable = false)
    @Builder.Default
    private DeliveryState deliveryState = DeliveryState.CREATED;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        // Если state не задан, ставим CREATED (на случай если кто-то создаст через new Delivery())
        if (this.deliveryState == null) {
            this.deliveryState = DeliveryState.CREATED;
        }
    }
}
