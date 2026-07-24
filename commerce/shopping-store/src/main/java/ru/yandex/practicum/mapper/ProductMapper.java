    package ru.yandex.practicum.mapper;

    import ru.yandex.practicum.ProductDto;
    import ru.yandex.practicum.entity.Product;

    public class ProductMapper {
        public static ProductDto toDto (Product p) {
            return ProductDto.builder()
                    .productId(p.getProductId())
                    .productName(p.getProductName())
                    .description(p.getDescription())
                    .imageSrc(p.getImageSrc())
                    .quantityState(p.getQuantityState())
                    .productState(p.getProductState())
                    .productCategory(p.getProductCategory())
                    .price(p.getPrice())
                    .build();
        }

        public static Product toEntity (ProductDto dto) {
            return Product.builder()
                    .productId(dto.getProductId())
                    .productName(dto.getProductName())
                    .description(dto.getDescription())
                    .imageSrc(dto.getImageSrc())
                    .quantityState(dto.getQuantityState())
                    .productState(dto.getProductState())
                    .productCategory(dto.getProductCategory())
                    .price(dto.getPrice())
                    .build();
        }
    }
