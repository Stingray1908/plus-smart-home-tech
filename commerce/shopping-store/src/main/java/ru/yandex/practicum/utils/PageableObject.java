package ru.yandex.practicum.utils;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class PageableObject {
    Long offset;
    List<SortObject> sort;
    boolean unpaged;
    boolean paged;
    Integer pageNumber;
    Integer pageSize;
}

