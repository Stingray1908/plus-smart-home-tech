package ru.yandex.practicum.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.yandex.practicum.entity.Scenario;

import java.util.List;
import java.util.Optional;

public interface ScenarioRepository extends JpaRepository<Scenario, Long> {

    // ИСПРАВЛЕНО: параметр hubId — String, и делаем JOIN FETCH для conditions
    @Query("SELECT s FROM Scenario s LEFT JOIN FETCH s.conditions WHERE s.hub.id = :hubId")
    List<Scenario> findByHubWithConditions(@Param("hubId") String hubId);

    Optional<Scenario> findByHubIdAndName(String hubId, String name);
}
