package ru.yandex.practicum.service;

import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.yandex.practicum.api.OrderServiceApi;
import ru.yandex.practicum.api.WarehouseServiceApi;
import ru.yandex.practicum.dto.*;
import ru.yandex.practicum.entity.Delivery;
import ru.yandex.practicum.exception.NoDeliveryFoundException;
import ru.yandex.practicum.exception.NotEnoughInfoInOrderToCalculateException;
import ru.yandex.practicum.repository.DeliveryRepository;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DeliveryService {

    private final DeliveryRepository deliveryRepository;
    private final WarehouseServiceApi warehouseServiceApi;
    private final OrderServiceApi orderServiceApi;

    public DeliveryDto saveDelivery(DeliveryDto dto) {
        UUID orderId = dto.getOrderId();
        if (orderId == null) {
            throw new IllegalArgumentException("orderId обязателен");
        }

        Delivery entity;
        if (dto.getDeliveryId() != null) {
            entity = deliveryRepository.findById(dto.getDeliveryId())
                    .orElseThrow(() -> new IllegalArgumentException("Доставка не найдена: " + dto.getDeliveryId()));
            log.info("Обновление доставки с ID {}", dto.getDeliveryId());
        } else {
            entity = new Delivery();
            log.info("Создание новой доставки (ID будет присвоен БД)");
        }

        fillAddress(entity, dto.getFromAddress(), true);
        fillAddress(entity, dto.getToAddress(), false);
        entity.setOrderId(orderId);

        if (dto.getDeliveryState() != null) {
            entity.setDeliveryState(dto.getDeliveryState());
        }

        Delivery saved = deliveryRepository.save(entity);
        log.info("Доставка сохранена. ID: {}, Заказ: {}", saved.getId(), saved.getOrderId());

        return toDto(saved);
    }

    public DeliveryDto markDeliverySuccessful(UUID orderId) {
        if (orderId == null) {
            throw new IllegalArgumentException("orderId обязателен");
        }

        List<Delivery> deliveries = deliveryRepository.findByOrderId(orderId);
        if (deliveries == null || deliveries.isEmpty()) {
            throw new NoDeliveryFoundException("Доставка для заказа не найдена: " + orderId, 404);
        }

        Delivery delivery = deliveries.getFirst();
        if (delivery.getDeliveryState() == DeliveryState.DELIVERED) {
            log.warn("Попытка повторно установить DELIVERED для доставки ID={}", delivery.getId());
            return toDto(delivery);
        }

        delivery.setDeliveryState(DeliveryState.DELIVERED);
        delivery = deliveryRepository.save(delivery);
        log.info("Доставка заказа {} переведена в DELIVERED", orderId);

        try {
            orderServiceApi.handleDelivery(orderId);
            log.info("Статус заказа {} успешно обновлён в order-service: DELIVERED", orderId);
        } catch (FeignException e) {
            log.error("Не удалось обновить статус заказа в order-service для orderId={}", orderId, e);
            throw e;
        }

        return toDto(delivery);
    }

    public DeliveryDto markDeliveryFailed(UUID orderId) {
        if (orderId == null) {
            throw new IllegalArgumentException("orderId обязателен");
        }

        List<Delivery> deliveries = deliveryRepository.findByOrderId(orderId);
        if (deliveries == null || deliveries.isEmpty()) {
            throw new NoDeliveryFoundException("Доставка для заказа не найдена: " + orderId, 404);
        }

        Delivery delivery = deliveries.getFirst();
        if (delivery.getDeliveryState() == DeliveryState.FAILED) {
            log.info("Доставка заказа {} уже в статусе FAILED", orderId);
            return toDto(delivery);
        }

        delivery.setDeliveryState(DeliveryState.FAILED);
        delivery = deliveryRepository.save(delivery);
        log.info("Доставка заказа {} переведена в FAILED", orderId);

        try {
            orderServiceApi.handleDeliveryFailed(orderId);
            log.info("Статус заказа {} успешно обновлён в order-service: DELIVERY_FAILED", orderId);
        } catch (FeignException e) {
            log.error("Не удалось обновить статус заказа в order-service для orderId={}", orderId, e);
            throw e;
        }

        return toDto(delivery);
    }

    public DeliveryDto markDeliveryPicked(UUID orderId) {
        if (orderId == null) {
            throw new IllegalArgumentException("orderId обязателен");
        }

        List<Delivery> deliveries = deliveryRepository.findByOrderId(orderId);
        if (deliveries == null || deliveries.isEmpty()) {
            throw new NoDeliveryFoundException("Доставка для заказа не найдена: " + orderId, 404);
        }

        Delivery delivery = deliveries.getFirst();
        boolean deliveryUpdated = false;

        if (!(delivery.getDeliveryState() == DeliveryState.DELIVERED
                || delivery.getDeliveryState() == DeliveryState.CANCELLED
                || delivery.getDeliveryState() == DeliveryState.FAILED)) {
            delivery.setDeliveryState(DeliveryState.IN_PROGRESS);
            delivery = deliveryRepository.save(delivery);
            deliveryUpdated = true;
            log.info("Доставка заказа {} переведена в IN_PROGRESS", orderId);
        } else {
            log.warn("Доставка уже в финальном статусе {}, доставка не обновляется, но продолжаем по цепочке",
                    delivery.getDeliveryState());
        }

        try {
            orderServiceApi.handleAssembly(orderId);
            log.info("Заказ {} успешно переведён в статус ASSEMBLED", orderId);
        } catch (FeignException e) {
            log.error("Не удалось обновить статус заказа в order-service для orderId={}", orderId, e);
            throw e;
        }

        ShippedToDeliveryRequest request = new ShippedToDeliveryRequest(orderId, delivery.getId());
        try {
            warehouseServiceApi.markOrderShipped(request);
            log.info("Заказ {} и доставка {} связаны на складе", orderId, delivery.getId());
        } catch (FeignException e) {
            log.error("Не удалось связать заказ {} с доставкой {} на складе", orderId, delivery.getId(), e);
            throw e;
        }

        return toDto(delivery);
    }

    public Double calculateCost(OrderDto orderDto) {
        if (orderDto.getOrderId() == null) {
            throw new IllegalArgumentException("orderId обязателен для расчёта стоимости");
        }

        List<Delivery> deliveries = deliveryRepository.findByOrderId(orderDto.getOrderId());
        if (deliveries == null || deliveries.isEmpty()) {
            log.warn("Не найдена доставка для заказа {}", orderDto.getOrderId());
            throw new NotEnoughInfoInOrderToCalculateException(
                    "Доставка для заказа не найдена",
                    "Не удалось рассчитать стоимость: для заказа не создана доставка",
                    404, null
            );
        }

        if (deliveries.size() > 1) {
            String deliveryIds = deliveries.stream()
                    .map(Delivery::getId)
                    .map(UUID::toString)
                    .collect(Collectors.joining(", "));
            log.error("Обнаружено {} доставок для заказа {}: {}. Ожидается ровно одна.",
                    deliveries.size(), orderDto.getOrderId(), deliveryIds);
            throw new IllegalStateException(
                    "Для заказа " + orderDto.getOrderId() + " найдено более одной доставки: " + deliveryIds
            );
        }

        Delivery delivery = deliveries.getFirst();
        log.debug("Найдена доставка ID: {} для заказа ID: {}", delivery.getId(), orderDto.getOrderId());

        String warehouseLocation = delivery.getFromCity();
        String fromStreet = delivery.getFromStreet();
        String toStreet = delivery.getToStreet();

        if (warehouseLocation == null) {
            log.error("В доставке ID={} не указан адрес склада (fromCity). Невозможно рассчитать стоимость.", delivery.getId());
            throw new IllegalStateException("Адрес склада не указан в данных доставки");
        }

        boolean isAddress1 = "ADDRESS_1".equals(warehouseLocation);
        boolean isAddress2 = "ADDRESS_2".equals(warehouseLocation);

        if (!isAddress1 && !isAddress2) {
            throw new IllegalArgumentException(
                    "Неизвестный адрес склада в записи доставки: '" + warehouseLocation +
                            "'. Ожидалось ADDRESS_1 или ADDRESS_2. Проверьте данные доставки."
            );
        }

        double baseRate = 5.0;
        double currentSum = baseRate;

        double warehouseMultiplier = isAddress1 ? 1.0 : 2.0;
        currentSum += baseRate * warehouseMultiplier;
        log.debug("Коэффициент склада ({}): множитель {}, итог {}", warehouseLocation, warehouseMultiplier, currentSum);

        boolean isFragile = Boolean.TRUE.equals(orderDto.getFragile());
        if (isFragile) {
            currentSum += currentSum * 0.2;
        }
        log.debug("Учёт хрупкости ({}): итог {}", isFragile, currentSum);

        double weight = (orderDto.getDeliveryWeight() != null) ? orderDto.getDeliveryWeight() : 0.0;
        currentSum += weight * 0.3;
        log.debug("Учёт веса ({} кг): итог {}", weight, currentSum);

        double volume = (orderDto.getDeliveryVolume() != null) ? orderDto.getDeliveryVolume() : 0.0;
        currentSum += volume * 0.2;
        log.debug("Учёт объёма ({} м³): итог {}", volume, currentSum);

        if (fromStreet != null && toStreet != null) {
            if (!fromStreet.equalsIgnoreCase(toStreet)) {
                log.debug("Улицы разные (Склад: {}, Клиент: {}). Добавляем коэффициент.", fromStreet, toStreet);
                currentSum += currentSum * 0.2;
            } else {
                log.debug("Улицы совпадают ({}). Коэффициент не применяется.", fromStreet);
            }
        } else {
            log.warn("Одна из улиц не заполнена (Склад: '{}', Клиент: '{}'). Применяем коэффициент за дальнюю доставку.",
                    fromStreet, toStreet);
            currentSum += currentSum * 0.2;
        }

        double finalCost = Math.round(currentSum * 100.0) / 100.0;
        log.info("Расчёт стоимости доставки для заказа {} завершён. Итоговая стоимость: {}", orderDto.getOrderId(), finalCost);
        return finalCost;
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
