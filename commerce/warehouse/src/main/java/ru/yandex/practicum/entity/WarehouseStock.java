package ru.yandex.practicum.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name = "warehouse_stock")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WarehouseStock {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", unique = true, nullable = false)
    private UUID productId;

    private double weight;      // вес единицы
    private double volume;      // объём единицы
    private boolean fragile;    // хрупкость
    private long quantity;      // остаток
}
