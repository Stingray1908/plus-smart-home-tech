package ru.yandex.practicum.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.yandex.practicum.entity.CartItem;

import java.util.List;
import java.util.UUID;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {

    @Modifying
    @Query("DELETE FROM CartItem c WHERE c.shoppingCart.shoppingCartId = :cartId")
    void deleteByCartId(@Param("cartId") UUID cartId);

    // Явный JPQL вместо findByCartId
    @Query("SELECT c FROM CartItem c WHERE c.shoppingCart.shoppingCartId = :cartId")
    List<CartItem> findByCartId(@Param("cartId") UUID cartId);

    @Modifying
    @Query("DELETE FROM CartItem c WHERE c.shoppingCart.shoppingCartId = :cartId AND c.productId IN :productIds")
    void deleteByCartIdAndProductIds(@Param("cartId") UUID cartId, @Param("productIds") List<UUID> productIds);
}
