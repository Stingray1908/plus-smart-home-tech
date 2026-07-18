package ru.yandex.practicum.dto;

import lombok.Builder;
import lombok.Data;
import ru.yandex.practicum.utils.PageableObject;
import ru.yandex.practicum.utils.SortObject;

import java.util.List;

@Data
@Builder
public class PageProductDto {
    long totalElements;                 // integer($int64)
    int totalPages;                      // integer($int32)
    boolean first;
    boolean last;
    int size;                            // integer($int32)
    List<ProductDto> content;           // [ProductDto{...}]
    int number;                          // integer($int32)
    List<SortObject> sort;              // [SortObject{...}]
    PageableObject pageable;            // PageableObject{...}
    int numberOfElements;                // integer($int32)
    boolean empty;
}

