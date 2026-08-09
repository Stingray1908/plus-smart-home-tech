package ru.yandex.practicum.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.yandex.practicum.entity.WarehouseStock;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WarehouseStockRepository extends JpaRepository<WarehouseStock, UUID> {

    List<WarehouseStock> findByProductIdIn(List<UUID> productIds);

    Optional<WarehouseStock> findByProductId(UUID productId);

    @Query("SELECT s FROM WarehouseStock s WHERE s.productId IN :productIds")
    List<WarehouseStock> findAllByProductIdIn(@Param("productIds") Collection<UUID> productIds);

}
