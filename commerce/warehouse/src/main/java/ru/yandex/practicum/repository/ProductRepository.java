package ru.yandex.practicum.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.yandex.practicum.entity.Product;
import ru.yandex.practicum.entity.WarehouseStock;

import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID> {
}
