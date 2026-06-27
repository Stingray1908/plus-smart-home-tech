package ru.yandex.practicum.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.yandex.practicum.entity.ScenarioCondition;
import ru.yandex.practicum.entity.ScenarioConditionId;

import java.util.List;


public interface ScenarioConditionRepository extends JpaRepository<ScenarioCondition, ScenarioConditionId> {
    List<ScenarioCondition> findByScenarioScenarioId(Long scenarioId);
}
