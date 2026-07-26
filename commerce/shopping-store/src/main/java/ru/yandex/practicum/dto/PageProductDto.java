package ru.yandex.practicum.dto;

import lombok.Builder;
import lombok.Data;
import ru.yandex.practicum.utils.PageableObject;
import ru.yandex.practicum.utils.SortObject;

import java.util.List;

@Data
@Builder
public class PageProductDto {
    private long totalElements;
    private int totalPages;
    private boolean first;
    private boolean last;
    private int size;
    private List<ProductDto> content;
    private int number;
    private List<SortObject> sort;
    private PageableObject pageable;
    private int numberOfElements;
    private boolean empty;
}

