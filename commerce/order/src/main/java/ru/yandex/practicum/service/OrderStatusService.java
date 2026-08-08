package ru.yandex.practicum.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.yandex.practicum.dto.OrderStatus;
import ru.yandex.practicum.entity.Order;
import ru.yandex.practicum.exception.NoOrderFoundException;
import ru.yandex.practicum.repository.OrderRepository;

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

    }

    private boolean isValidTransition(OrderStatus current, OrderStatus next) {
        if (current == OrderStatus.CANCELED || current == OrderStatus.COMPLETED) {
            return false;
        }

        return switch (current) {
            case NEW -> next == OrderStatus.ASSEMBLED || next == OrderStatus.PAYMENT_FAILED;
            case ASSEMBLED -> next == OrderStatus.ON_DELIVERY || next == OrderStatus.ASSEMBLY_FAILED;
            case ON_DELIVERY -> next == OrderStatus.COMPLETED || next == OrderStatus.DELIVERY_FAILED;
            case PAID -> next == OrderStatus.ASSEMBLED;
            default -> false;
        };
    }
}
