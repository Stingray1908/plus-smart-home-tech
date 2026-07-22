package ru.yandex.practicum.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "products")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class Product {

    @Id
    private UUID id;

    private double weight;      // вес 1 единицы
    private double volume;     // объём 1 единицы
    private boolean fragile;   // хрупкий ли товар
}
