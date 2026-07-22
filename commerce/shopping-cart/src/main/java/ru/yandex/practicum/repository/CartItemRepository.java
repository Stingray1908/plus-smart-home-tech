package ru.yandex.practicum.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;
import ru.yandex.practicum.entity.CartItem;

import java.util.UUID;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {

    @Transactional
    @Modifying
    @Query("DELETE FROM CartItem c WHERE c.shoppingCart.shoppingCartId = :cartId")
    void deleteByCartId(UUID cartId);
}
