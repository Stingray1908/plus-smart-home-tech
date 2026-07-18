package ru.yandex.practicum.utils;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class PageableObject {
    Long offset;                     // integer($int64)
    List<SortObject> sort;           // [SortObject{...}]
    boolean unpaged;
    boolean paged;
    Integer pageNumber;               // integer($int32)
    Integer pageSize;                 // integer($int32)
}

