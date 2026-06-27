package ru.yandex.practicum.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.yandex.practicum.entity.Scenario;
import java.util.List;

public interface ScenarioRepository extends JpaRepository<Scenario, Long> {

        List<Scenario> findByHubId(Long hubId);
    }
