package ru.yandex.practicum.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.yandex.practicum.dto.OrderStatus;
import ru.yandex.practicum.entity.Order;
import ru.yandex.practicum.exception.NoOrderFoundException;
import ru.yandex.practicum.repository.OrderRepository;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderStatusService {

    private final OrderRepository orderRepository;

    @Transactional
    public void transitionTo(UUID orderId, OrderStatus newStatus) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new NoOrderFoundException("Заказ не найден: " + orderId, 404));

        if (!isValidTransition(order.getState(), newStatus)) {
            throw new IllegalStateException(
                    "Недопустимый переход статуса: из " + order.getState() + " в " + newStatus
            );
        }

        order.setState(newStatus);
        // orderRepository.save(order); // save вызывается автоматически в конце транзакции
    }

    /**
     * Простая машина состояний для спринта.
     * Дорабатывай эту логику, если в ТЗ появятся дополнительные правила.
     */
    private boolean isValidTransition(OrderStatus current, OrderStatus next) {
        // Из CANCELED и COMPLETED нельзя никуда идти
        if (current == OrderStatus.CANCELED || current == OrderStatus.COMPLETED) {
            return false;
        }

        // NEW -> ASSEMBLED / PAYMENT_FAILED
        if (current == OrderStatus.NEW) {
            return next == OrderStatus.ASSEMBLED || next == OrderStatus.PAYMENT_FAILED;
        }

        // ASSEMBLED -> IN_DELIVERY / ASSEMBLY_FAILED
        if (current == OrderStatus.ASSEMBLED) {
            return next == OrderStatus.ON_DELIVERY || next == OrderStatus.ASSEMBLY_FAILED;
        }

        // IN_DELIVERY -> COMPLETED / DELIVERY_FAILED
        if (current == OrderStatus.ON_DELIVERY) {
            return next == OrderStatus.COMPLETED || next == OrderStatus.DELIVERY_FAILED;
        }

        // PAID -> ASSEMBLED (если вдруг оплата раньше сборки)
        if (current == OrderStatus.PAID) {
            return next == OrderStatus.ASSEMBLED;
        }

        return false;
    }

    // Удобные методы под твои эндпоинты
    public void markPaymentFailed(UUID orderId) {
        transitionTo(orderId, OrderStatus.PAYMENT_FAILED);
    }

    public void markAssemblyStarted(UUID orderId) {
        transitionTo(orderId, OrderStatus.ASSEMBLED);
    }

    public void markDeliveryStarted(UUID orderId) {
        transitionTo(orderId, OrderStatus.ON_DELIVERY);
    }

    public void markCompleted(UUID orderId) {
        transitionTo(orderId, OrderStatus.COMPLETED);
    }
}
