package ru.yandex.practicum.service;

import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.yandex.practicum.api.WarehouseServiceApi;
import ru.yandex.practicum.dto.BookedProductsDto;
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
    private final WarehouseServiceApi warehouseClient;

    @Transactional
    public ShoppingCartDto addToCart(String username, Map<UUID, Long> products) {
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

        ShoppingCartDto cartForCheck = ShoppingCartDto.builder()
                .shoppingCartId(cartId)
                .products(products)
                .build();

        BookedProductsDto checkResult;
        try {
            checkResult = warehouseClient.check(cartForCheck).getBody();
        } catch (FeignException e) {
            throw new IllegalStateException(
                    "Не удалось добавить товары: проверка склада не пройдена. " + e.getMessage(), e
            );
        }

        cartItemRepository.deleteByCartId(cartId);

        List<CartItem> items = products.entrySet().stream()
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
                .toList();

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
            return toDto(cart, items);
        }

        matchingItems.forEach(item -> cartItemRepository.deleteById(item.getId()));

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

    @Transactional(readOnly = true)
    public ShoppingCartDto getShoppingCart(String username) {
        validateUsername(username);

        ShoppingCart cart = getOrCreateCart(username);
        UUID cartId = cart.getShoppingCartId();

        List<CartItem> items = cartItemRepository.findByCartId(cartId);
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
                        CartItem    ::getQuantity,
                        Long::sum
                ));

        return ShoppingCartDto.builder()
                .shoppingCartId(cart.getShoppingCartId())
                .products(products)
                .build();
    }
}
