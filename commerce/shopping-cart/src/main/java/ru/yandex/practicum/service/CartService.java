package ru.yandex.practicum.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.yandex.practicum.dto.AddToCartDto;
import ru.yandex.practicum.dto.ShoppingCartDto;
import ru.yandex.practicum.entity.CartItem;
import ru.yandex.practicum.entity.ShoppingCart;
import ru.yandex.practicum.exception.CartNotActiveException;
import ru.yandex.practicum.exception.CartNotFoundException;
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
        validateUsername(username);

        ShoppingCart cart = getOrCreateCart(username);
        UUID cartId = cart.getShoppingCartId();

        // Если корзина неактивна — запрещаем добавление
        if (!cart.isActive()) {
            throw new CartNotActiveException(
                    "User cart is deactivated",
                    "Корзина пользователя " + username + " неактивна. Добавление товаров запрещено.",
                    403
            );
        }

        // Удаляем старые товары (полная замена содержимого)
        cartItemRepository.deleteByCartId(cartId);

        // Добавляем новые товары
        List<CartItem> items = request.getProducts().entrySet().stream()
                .map(e -> CartItem.builder()
                        .shoppingCart(cart)
                        .productId(e.getKey())
                        .quantity(e.getValue())
                        .build())
                .collect(Collectors.toList());

        cartItemRepository.saveAll(items);

        return toDto(cart, items);
    }

    @Transactional
    public void deactivateCart(String username) {
        validateUsername(username);

        Optional<ShoppingCart> opt = shoppingCartRepository.findByUsername(username);

        if (opt.isEmpty()) {
            // Вариант 1: выбрасываем ошибку, если корзины нет (строгий режим)
            throw new CartNotFoundException(
                    "Cart not found",
                    "Корзины для пользователя " + username + " не найдено",
                    404
            );

            // Вариант 2 (если нужна идемпотентность): просто ничего не делаем
            // return;
        }

        ShoppingCart cart = opt.get();
        cart.setActive(false);
        shoppingCartRepository.save(cart);
    }
    
    private void validateUsername(String username) {
        if (username == null || username.isBlank()) {
            throw new NotAuthorizedUserException(
                    "Username is blank",
                    "Имя пользователя не должно быть пустым",
                    401
            );
        }
    }

    private ShoppingCart getOrCreateCart(String username) {
        Optional<ShoppingCart> opt = shoppingCartRepository.findByUsername(username);

        if (opt.isPresent()) {
            return opt.get();
        }

        // Создаём новую корзину — она всегда активная
        ShoppingCart newCart = new ShoppingCart();
        newCart.setShoppingCartId(java.util.UUID.randomUUID());
        newCart.setUsername(username);
        newCart.setActive(true);

        return shoppingCartRepository.save(newCart);
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
