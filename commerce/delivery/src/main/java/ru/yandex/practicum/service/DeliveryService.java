package ru.yandex.practicum.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.yandex.practicum.api.WarehouseServiceApi;
import ru.yandex.practicum.dto.*;
import ru.yandex.practicum.entity.Delivery;
import ru.yandex.practicum.exception.NoDeliveryFoundException;
import ru.yandex.practicum.exception.NotEnoughInfoInOrderToCalculateException;
import ru.yandex.practicum.repository.DeliveryRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DeliveryService {

    private final DeliveryRepository deliveryRepository;
    private final WarehouseServiceApi warehouseServiceApi;

    public DeliveryDto saveDelivery(DeliveryDto dto) {
        UUID orderId = dto.getOrderId();

        if (orderId == null) {
            throw new IllegalArgumentException("orderId обязателен");
        }

        Delivery entity;

        // Логика: если ID есть -> обновляем, если нет -> создаем (ID сгенерирует БД)
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

    //
    public Double calculateCost(OrderDto orderDto) {
        if (orderDto.getOrderId() == null) {
            throw new IllegalArgumentException("orderId обязателен для расчёта стоимости");
        }

        // 1. Находим доставку по заказу
        List<Delivery> deliveries = deliveryRepository.findByOrderId(orderDto.getOrderId());

        if (deliveries == null || deliveries.isEmpty()) {
            log.warn("Не найдена доставка для заказа {}", orderDto.getOrderId());
            throw new NotEnoughInfoInOrderToCalculateException(
                    "Доставка для заказа не найдена",
                    "Не удалось рассчитать стоимость: для заказа не создана доставка",
                    404, null
            );
        }

        // Защита: если доставок больше одной
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

        String warehouseLocation = delivery.getFromCity(); // Сюда при создании положили "ADDRESS_1" или "ADDRESS_2"
        String fromStreet = delivery.getFromStreet();     // Улица склада
        String toStreet = delivery.getToStreet();         // Улица клиента

        if (warehouseLocation == null) {
            log.error("В доставке ID={} не указан адрес склада (fromCity). Невозможно рассчитать стоимость.", delivery.getId());
            throw new IllegalStateException("Адрес склада не указан в данных доставки");
        }

        boolean isAddress1 = "ADDRESS_1".equals(warehouseLocation);
        boolean isAddress2 = "ADDRESS_2".equals(warehouseLocation);

        if (!isAddress1 && !isAddress2) {
            // Если там что-то другое (опечатка при создании), кидаем понятную ошибку
            throw new IllegalArgumentException(
                    "Неизвестный адрес склада в записи доставки: '" + warehouseLocation +
                            "'. Ожидалось ADDRESS_1 или ADDRESS_2. Проверьте данные доставки."
            );
        }

        // 2. Базовая ставка
        double baseRate = 5.0;
        double currentSum = baseRate;

        // 3. Коэффициент склада
        double warehouseMultiplier = isAddress1 ? 1.0 : 2.0;
        currentSum = currentSum + (baseRate * warehouseMultiplier);
        log.debug("Коэффициент склада ({}): множитель {}, итог {}", warehouseLocation, warehouseMultiplier, currentSum);

        // 4. Хрупкость (берём из DTO заказа, так как это свойство груза)
        boolean isFragile = Boolean.TRUE.equals(orderDto.getFragile());
        if (isFragile) {
            currentSum = currentSum + (currentSum * 0.2);
        }
        log.debug("Учёт хрупкости ({}): итог {}", isFragile, currentSum);

        // 5. Вес (из DTO заказа)
        double weight = (orderDto.getDeliveryWeight() != null) ? orderDto.getDeliveryWeight() : 0.0;
        currentSum = currentSum + (weight * 0.3);
        log.debug("Учёт веса ({} кг): итог {}", weight, currentSum);

        // 6. Объём (из DTO заказа)
        double volume = (orderDto.getDeliveryVolume() != null) ? orderDto.getDeliveryVolume() : 0.0;
        currentSum = currentSum + (volume * 0.2);
        log.debug("Учёт объёма ({} м³): итог {}", volume, currentSum);

        // 7. Сравнение улиц (СКЛАД vs КЛИЕНТ)
        // Теперь мы сравниваем delivery.getFromStreet() и delivery.getToStreet()
        if (fromStreet != null && toStreet != null) {
            if (!fromStreet.equalsIgnoreCase(toStreet)) {
                log.debug("Улицы разные (Склад: {}, Клиент: {}). Добавляем коэффициент.", fromStreet, toStreet);
                currentSum = currentSum + (currentSum * 0.2);
            } else {
                log.debug("Улицы совпадают ({}). Коэффициент не применяется.", fromStreet);
            }
        } else {
            // Если улицы не заполнены, считаем доставку дальней (по умолчанию, как в ТЗ)
            log.warn("Одна из улиц не заполнена (Склад: '{}', Клиент: '{}'). Применяем коэффициент за дальнюю доставку.",
                    fromStreet, toStreet);
            currentSum = currentSum + (currentSum * 0.2);
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
