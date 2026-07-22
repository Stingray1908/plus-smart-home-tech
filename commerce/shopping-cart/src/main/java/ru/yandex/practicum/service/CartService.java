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
import ru.yandex.practicum.exception.NoProductsInShoppingCartException;
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

        if (!cart.isActive()) {
            throw new CartNotActiveException(
                    "User cart is deactivated",
                    "Корзина пользователя " + username + " неактивна. Добавление товаров запрещено.",
                    403
            );
        }

        cartItemRepository.deleteByCartId(cartId);

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
            throw new CartNotFoundException(
                    "Cart not found",
                    "Корзины для пользователя " + username + " не найдено",
                    404
            );
        }

        ShoppingCart cart = opt.get();
        cart.setActive(false);
        shoppingCartRepository.save(cart);
    }

    @Transactional
    public ShoppingCartDto removeProductsFromCart(String username, List<UUID> productIds) {
        validateUsername(username);

        if (productIds == null || productIds.isEmpty()) {
            ShoppingCart cart = getOrCreateCart(username);
            List<CartItem> items = cartItemRepository.findByCartId(cart.getShoppingCartId());
            return toDto(cart, items);
        }

        ShoppingCart cart = getOrCreateCart(username);
        UUID cartId = cart.getShoppingCartId();

        if (!cart.isActive()) {
            throw new CartNotActiveException(
                    "User cart is deactivated",
                    "Корзина пользователя " + username + " неактивна. Удаление товаров запрещено.",
                    403
            );
        }

        Set<UUID> existingIds = cartItemRepository.findByCartId(cartId).stream()
                .map(CartItem::getProductId)
                .collect(Collectors.toSet());

        if (productIds.stream().anyMatch(id -> !existingIds.contains(id))) {
            throw new NoProductsInShoppingCartException(
                    "Some product IDs not found in cart",
                    "Некоторые товары не найдены в корзине",
                    400
            );
        }

        cartItemRepository.deleteByCartIdAndProductIds(cartId, productIds);
        List<CartItem> updatedItems = cartItemRepository.findByCartId(cartId);
        return toDto(cart, updatedItems);
    }

    @Transactional
    public ShoppingCartDto changeQuantity(String username, UUID productId, long newQuantity) {
        validateUsername(username);

        if (newQuantity < 0) {
            throw new IllegalArgumentException("Количество не может быть отрицательным");
        }

        ShoppingCart cart = getOrCreateCart(username);
        UUID cartId = cart.getShoppingCartId();

        if (!cart.isActive()) {
            throw new CartNotActiveException(
                    "User cart is deactivated",
                    "Корзина пользователя " + username + " неактивна. Изменение количества товаров запрещено.",
                    403
            );
        }

        List<CartItem> items = cartItemRepository.findByCartId(cartId);
        List<CartItem> matchingItems = items.stream()
                .filter(i -> i.getProductId().equals(productId))
                .collect(Collectors.toList());

        if (matchingItems.isEmpty()) {
            throw new NoProductsInShoppingCartException(
                    "Product not found in cart",
                    "Товар с ID " + productId + " не найден в корзине",
                    400
            );
        }

        long totalQuantity = matchingItems.stream()
                .mapToLong(CartItem::getQuantity)
                .sum();

        if (totalQuantity == newQuantity) {
            // Ничего не меняем
            return toDto(cart, items);
        }

        // Удаляем старые строки по ID (теперь метод есть в репо)
        matchingItems.forEach(item -> cartItemRepository.deleteById(item.getId()));

        // Если новое количество > 0 — добавляем одну строку
        if (newQuantity > 0) {
            CartItem newItem = CartItem.builder()
                    .shoppingCart(cart)
                    .productId(productId)
                    .quantity(newQuantity)
                    .build();
            cartItemRepository.save(newItem);
        }

        List<CartItem> updatedItems = cartItemRepository.findByCartId(cartId);
        return toDto(cart, updatedItems);
    }

    @Transactional(readOnly = true) // readOnly оптимизирует запрос к БД
    public ShoppingCartDto getShoppingCart(String username) {
        // 1. Валидируем имя (переиспользуем твой метод)
        validateUsername(username);

        // 2. Получаем корзину (создаем, если нет - переиспользуем твой метод)
        ShoppingCart cart = getOrCreateCart(username);
        UUID cartId = cart.getShoppingCartId();

        // 3. Получаем товары
        List<CartItem> items = cartItemRepository.findByCartId(cartId);

        // 4. Формируем DTO (переиспользуем твой метод)
        return toDto(cart, items);
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

        ShoppingCart newCart = new ShoppingCart();
        newCart.setShoppingCartId(UUID.randomUUID());
        newCart.setUsername(username);
        newCart.setActive(true);

        return shoppingCartRepository.save(newCart);
    }

    private ShoppingCartDto toDto(ShoppingCart cart, List<CartItem> items) {
        Map<UUID, Long> products = items.stream()
                .collect(Collectors.toMap(
                        CartItem::getProductId,
                        item -> (long) item.getQuantity(),
                        (v1, v2) -> v1 + v2
                ));

        return ShoppingCartDto.builder()
                .shoppingCartId(cart.getShoppingCartId())
                .products(products)
                .build();
    }
}
