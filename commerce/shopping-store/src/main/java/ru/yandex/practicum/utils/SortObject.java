package ru.yandex.practicum.utils;


import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SortObject {
    String direction;
    String nullHandling;
    boolean ascending;
    String property;
    boolean ignoreCase;
}
