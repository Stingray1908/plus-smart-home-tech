package ru.yandex.practicum.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.yandex.practicum.entity.Delivery;

import java.util.UUID;

public interface DeliveryRepository extends JpaRepository<Delivery, UUID> {
}
