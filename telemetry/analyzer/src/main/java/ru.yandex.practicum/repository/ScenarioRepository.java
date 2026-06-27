package ru.yandex.practicum.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import ru.yandex.practicum.entity.Scenario;
import java.util.List;
import java.util.Optional;

public interface ScenarioRepository extends JpaRepository<Scenario, Long> {

    List<Scenario> findByHubId(String hubId);

    Optional<Scenario> findByHubIdAndName(@Param("hubId") String hubId, @Param("name") String name);

    /**
     * Один запрос: сценарий + его условия + его действия.
     * Это убирает N+1 проблему.
     */
    @Query("SELECT s FROM Scenario s " +
            "JOIN FETCH s.conditions " +
            "JOIN FETCH s.actions " +
            "WHERE s.hub.hubId = :hubId")
    List<Scenario> findByHubIdWithDetails(@Param("hubId") String hubId);
}
