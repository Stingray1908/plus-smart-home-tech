package ru.yandex.practicum.dto;

import lombok.Builder;
import lombok.Data;
import ru.yandex.practicum.utils.PageableObject;
import ru.yandex.practicum.utils.SortObject;

import java.util.List;

@Data
@Builder
public class PageProductDto {
    long totalElements;
    int totalPages;
    boolean first;
    boolean last;
    int size;
    List<ProductDto> content;
    int number;
    List<SortObject> sort;
    PageableObject pageable;
    int numberOfElements;
    boolean empty;
}

