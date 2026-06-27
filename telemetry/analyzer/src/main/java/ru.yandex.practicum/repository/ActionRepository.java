package ru.yandex.practicum.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.yandex.practicum.entity.Action;
import java.util.List;

public interface ActionRepository extends JpaRepository<Action, Long> {
}
