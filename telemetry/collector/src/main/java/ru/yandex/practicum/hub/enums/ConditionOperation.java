
package ru.yandex.practicum.hub.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum ConditionOperation {
    EQUALS("equals"),
    GREATER_THAN("greater_than"),
    LOWER_THAN("lower_than");

    private final String value;
}
