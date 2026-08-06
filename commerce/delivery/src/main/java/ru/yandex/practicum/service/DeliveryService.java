package ru.yandex.practicum.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.yandex.practicum.api.WarehouseServiceApi;
import ru.yandex.practicum.dto.*;
import ru.yandex.practicum.entity.Delivery;
import ru.yandex.practicum.exception.NoDeliveryFoundException;
import ru.yandex.practicum.repository.DeliveryRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class DeliveryService {

    private final DeliveryRepository deliveryRepository;
    private final WarehouseServiceApi warehouseServiceApi;

    public DeliveryDto saveDelivery(DeliveryDto dto) {
        UUID deliveryId = dto.getDeliveryId();
        UUID orderId = dto.getOrderId();

        if (orderId == null) {
            throw new IllegalArgumentException("orderId обязателен");
        }

        Delivery entity;

        // Логика создания/обновления (твоя существующая)
        if (deliveryId != null) {
            entity = deliveryRepository.findById(deliveryId)
                    .orElseThrow(() -> new IllegalArgumentException("Доставка не найдена: " + deliveryId));
            log.info("Обновление доставки с ID {}", deliveryId);
        } else {
            entity = new Delivery();
            entity.setId(UUID.randomUUID()); // <-- deliveryId генерируется здесь
            entity.setDeliveryState(DeliveryState.CREATED);
            log.info("Создание новой доставки с ID {}", entity.getId());
        }

        fillAddress(entity, dto.getFromAddress(), true);
        fillAddress(entity, dto.getToAddress(), false);
        entity.setOrderId(orderId);

        if (dto.getDeliveryState() != null && deliveryId != null) {
            entity.setDeliveryState(dto.getDeliveryState());
        }

        Delivery saved = deliveryRepository.save(entity);

        // ================= ВАЖНО: ДОБАВЛЯЕМ ЭТОТ БЛОК =================
        // Сразу после сохранения доставки в своей БД, сообщаем складу о привязке
        ShippedToDeliveryRequest req = new ShippedToDeliveryRequest();
        req.setOrderId(saved.getOrderId());
        req.setDeliveryId(saved.getId()); // <-- тот самый ID, который только что сгенерировали

        try {
            warehouseServiceApi.markOrderShipped(req);
            log.info("Склад успешно уведомлён о доставке ID={} для заказа {}", saved.getId(), saved.getOrderId());
        } catch (Exception e) {
            // Если склад недоступен, у тебя есть fallback.
            // В учебном проекте можно просто логировать и продолжать,
            // либо откатить транзакцию (throw e), если нужна строгая согласованность.
            log.error("Не удалось уведомить склад о доставке ID={}", saved.getId(), e);
            // Для курса часто достаточно логирования, т.к. fallback вернёт 200 OK
        }
        // =============================================================

        return toDto(saved);
    }

    public DeliveryDto markDeliverySuccessful(UUID orderId) {
        if (orderId == null) {
            throw new IllegalArgumentException("orderId обязателен");
        }

        // Ищем доставку по orderId: в ТЗ сказано, что в body — идентификатор заказа
        List<Delivery> deliveries = deliveryRepository.findByOrderId(orderId);

        if (deliveries == null || deliveries.isEmpty()) {
            // Spring превратит это в 404 с полным стеком (как в примере ТЗ)
            throw new NoDeliveryFoundException("Доставка для заказа не найдена: " + orderId, 404);
        }

        // По ТЗ — одна доставка на заказ. Если вдруг их несколько — берем первую
        Delivery delivery = deliveries.get(0);

        // Опционально: защита от повторного перевода в DELIVERED
        if (delivery.getDeliveryState() == DeliveryState.DELIVERED) {
            log.warn("Попытка повторно установить DELIVERED для доставки ID={}", delivery.getId());
            // Можно либо вернуть текущий DTO, либо выбросить ValidationException (400).
            // Для курса чаще просто возвращают текущее состояние.
        } else if (delivery.getDeliveryState() == DeliveryState.FAILED || delivery.getDeliveryState() == DeliveryState.CANCELLED) {
            // Если уже FAILED/CANCELLED — можно либо запретить, либо разрешить (зависит от ТЗ).
            // Здесь разрешаем, но логируем.
            log.info("Смена статуса с {} на DELIVERED для заказа {}", delivery.getDeliveryState(), orderId);
        }

        delivery.setDeliveryState(DeliveryState.DELIVERED);
        Delivery saved = deliveryRepository.save(delivery);

        log.info("Доставка заказа {} переведена в DELIVERED", orderId);
        return toDto(saved);
    }

    public DeliveryDto markDeliveryPicked(UUID orderId) {
        if (orderId == null) {
            throw new IllegalArgumentException("orderId обязателен");
        }

        List<Delivery> deliveries = deliveryRepository.findByOrderId(orderId);

        if (deliveries == null || deliveries.isEmpty()) {
            // Это даст 404 с полным стеком (как в ТЗ)
            throw new NoDeliveryFoundException("Доставка для заказа не найдена: " + orderId, 404);
        }

        Delivery delivery = deliveries.getFirst();

        // Логика статусов:
        // - Если уже DELIVERED/CANCELLED/FAILED — можно либо запретить, либо просто логировать и не менять.
        // Для учебной задачи чаще всего просто ставим IN_PROGRESS, если не финальный.
        if (delivery.getDeliveryState() == DeliveryState.DELIVERED
                || delivery.getDeliveryState() == DeliveryState.CANCELLED
                || delivery.getDeliveryState() == DeliveryState.FAILED) {
            log.warn("Попытка перевести в IN_PROGRESS доставку в финальном статусе {} для заказа {}",
                    delivery.getDeliveryState(), orderId);
            // Можно вернуть текущий DTO без изменений
            return toDto(delivery);
        }

        delivery.setDeliveryState(DeliveryState.IN_PROGRESS);
        Delivery saved = deliveryRepository.save(delivery);

        log.info("Доставка заказа {} переведена в IN_PROGRESS", orderId);
        return toDto(saved);
    }

    public DeliveryDto markDeliveryFailed(UUID orderId) {
        if (orderId == null) {
            throw new IllegalArgumentException("orderId обязателен");
        }

        List<Delivery> deliveries = deliveryRepository.findByOrderId(orderId);

        if (deliveries == null || deliveries.isEmpty()) {
            // Это даст 404 с полным стеком (как в ТЗ)
            throw new NoDeliveryFoundException("Доставка для заказа не найдена: " + orderId, 404);
        }

        Delivery delivery = deliveries.get(0);

        // Логирование, если пытаемся перевести в FAILED уже финальный статус
        if (delivery.getDeliveryState() == DeliveryState.DELIVERED) {
            log.warn("Попытка установить FAILED для доставки, которая уже в DELIVERED. Заказ: {}", orderId);
            // Можно либо запретить, либо разрешить — здесь разрешаем, но с предупреждением
        } else if (delivery.getDeliveryState() == DeliveryState.FAILED) {
            log.info("Доставка заказа {} уже в статусе FAILED", orderId);
            return toDto(delivery);
        }

        delivery.setDeliveryState(DeliveryState.FAILED);
        Delivery saved = deliveryRepository.save(delivery);

        log.info("Доставка заказа {} переведена в FAILED", orderId);
        return toDto(saved);
    }

    public Double calculateCost(OrderDto orderDto) {
        if (orderDto.getOrderId() == null) {
            throw new IllegalArgumentException("orderId обязателен");
        }

        // 1. Ищем доставку по заказу (требование ТЗ: 404 если нет доставки)
        List<Delivery> deliveries = deliveryRepository.findByOrderId(orderDto.getOrderId());
        if (deliveries == null || deliveries.isEmpty()) {
            throw new NoDeliveryFoundException("Доставка для заказа не найдена: " + orderDto.getOrderId(), 404);
        }
        Delivery delivery = deliveries.get(0);

        // 2. Получаем адрес склада через Feign
        AddressDto warehouseAddress = warehouseServiceApi.getAddress().getBody();
        String warehouseName = warehouseAddress.getCity(); // или street/country — зависит от того, что в ТЗ считается «названием склада»

        // 3. Базовая ставка
        double baseRate = 5.0;
        double currentSum = baseRate;

        // 4. Коэффициент склада: ADDRESS_1 → ×1, ADDRESS_2 → ×2, потом складываем с базовой ставкой
        double warehouseMultiplier = 1.0;
        if ("ADDRESS_1".equals(warehouseName)) {
            warehouseMultiplier = 1.0;
        } else if ("ADDRESS_2".equals(warehouseName)) {
            warehouseMultiplier = 2.0;
        } else {
            // Если склад с другим именем — можно либо кинуть ошибку, либо взять 1.0.
            // Для курса логичнее кинуть ошибку.
            throw new IllegalArgumentException("Неизвестный адрес склада: " + warehouseName);
        }

        currentSum = currentSum + (baseRate * warehouseMultiplier);

        // 5. Хрупкость: умножаем текущую сумму на 0.2 и прибавляем
        boolean isFragile = Boolean.TRUE.equals(orderDto.getFragile());
        if (isFragile) {
            currentSum = currentSum + (currentSum * 0.2);
        }

        // 6. Вес: добавляем вес × 0.3
        double weight = orderDto.getDeliveryWeight() != null ? orderDto.getDeliveryWeight() : 0.0;
        currentSum = currentSum + (weight * 0.3);

        // 7. Объём: добавляем объём × 0.2
        double volume = orderDto.getDeliveryVolume() != null ? orderDto.getDeliveryVolume() : 0.0;
        currentSum = currentSum + (volume * 0.2);

        // 8. Сравнение улиц: если fromStreet (склад) != toStreet (доставка) → добавляем currentSum × 0.2
        String fromStreet = warehouseAddress.getStreet();
        String toStreet = delivery.getToStreet(); // адрес доставки хранится в сущности Delivery

        if (fromStreet != null && toStreet != null && !fromStreet.equalsIgnoreCase(toStreet)) {
            currentSum = currentSum + (currentSum * 0.2);
        }

        log.info("Рассчитана стоимость доставки для заказа {}: {}", orderDto.getOrderId(), currentSum);
        return currentSum;
    }


    private void fillAddress(Delivery entity, AddressDto addr, boolean isFrom) {
        if (addr == null) return;
        if (isFrom) {
            entity.setFromCountry(addr.getCountry());
            entity.setFromCity(addr.getCity());
            entity.setFromStreet(addr.getStreet());
            entity.setFromHouse(addr.getHouse());
            entity.setFromFlat(addr.getFlat());
        } else {
            entity.setToCountry(addr.getCountry());
            entity.setToCity(addr.getCity());
            entity.setToStreet(addr.getStreet());
            entity.setToHouse(addr.getHouse());
            entity.setToFlat(addr.getFlat());
        }
    }

    private DeliveryDto toDto(Delivery e) {
        AddressDto fromAddr = AddressDto.builder()
                .country(e.getFromCountry())
                .city(e.getFromCity())
                .street(e.getFromStreet())
                .house(e.getFromHouse())
                .flat(e.getFromFlat())
                .build();

        AddressDto toAddr = AddressDto.builder()
                .country(e.getToCountry())
                .city(e.getToCity())
                .street(e.getToStreet())
                .house(e.getToHouse())
                .flat(e.getToFlat())
                .build();

        return DeliveryDto.builder()
                .deliveryId(e.getId())
                .fromAddress(fromAddr)
                .toAddress(toAddr)
                .orderId(e.getOrderId())
                .deliveryState(e.getDeliveryState())
                .build();
    }
}
