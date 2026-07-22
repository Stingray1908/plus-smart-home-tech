package ru.yandex.practicum.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.yandex.practicum.dto.AddToCartDto;
import ru.yandex.practicum.dto.ShoppingCartDto;
import ru.yandex.practicum.entity.CartItem;
import ru.yandex.practicum.entity.ShoppingCart;
import ru.yandex.practicum.exception.NotAuthorizedUserException;
import ru.yandex.practicum.repository.CartItemRepository;
import ru.yandex.practicum.repository.ShoppingCartRepository;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CartService {

    private final CartItemRepository cartItemRepository;
    private final ShoppingCartRepository shoppingCartRepository;

    @Transactional
    public ShoppingCartDto addToCart(String username, AddToCartDto request) {
        if (username == null || username.isBlank()) {
            throw new NotAuthorizedUserException(
                    "Username is blank",
                    "Имя пользователя не должно быть пустым",
                    401
            );
        }

        // 1. Ищем корзину по username. Если нет — создаём.
        Optional<ShoppingCart> opt = shoppingCartRepository.findByUsername(username);

        ShoppingCart cart;
        UUID cartId;

        if (opt.isPresent()) {
            cart = opt.get();
            cartId = cart.getShoppingCartId();
        } else {
            cart = new ShoppingCart();
            cart.setShoppingCartId(java.util.UUID.randomUUID());
            cart.setUsername(username);
            cart = shoppingCartRepository.save(cart);
            cartId = cart.getShoppingCartId();
        }
        final ShoppingCart finalCart = cart;

        // 2. Удаляем ВСЕ старые товары из корзины (полная замена)
        cartItemRepository.deleteByCartId(cartId);

        // 3. Добавляем новые товары из запроса
        List<CartItem> items = request.getProducts().entrySet().stream()
                .map(e -> CartItem.builder()
                        .shoppingCart(finalCart)
                        .productId(e.getKey())
                        .quantity(e.getValue())
                        .build())
                .collect(Collectors.toList());

        cartItemRepository.saveAll(items);

        return toDto(cart, items);
    }

    private ShoppingCartDto toDto(ShoppingCart cart, List<CartItem> items) {
        Map<UUID, Long> products = items.stream()
                .collect(Collectors.toMap(
                        CartItem::getProductId,
                        CartItem::getQuantity
                ));

        return ShoppingCartDto.builder()
                .shoppingCartId(cart.getShoppingCartId())
                .products(products)
                .build();
    }
}
