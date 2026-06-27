package ru.yandex.practicum.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.yandex.practicum.entity.Sensor;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SensorRepository extends JpaRepository<Sensor, String> {
    boolean existsByIdInAndHubId(@Param("ids") Collection<String> ids, @Param("hubId") String hubId);
    Optional<Sensor> findByIdAndHubId(@Param("id") String id, @Param("hubId") String hubId);
}
