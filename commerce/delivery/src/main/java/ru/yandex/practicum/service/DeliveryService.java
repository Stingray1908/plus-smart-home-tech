package ru.yandex.practicum.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.yandex.practicum.dto.AddressDto;
import ru.yandex.practicum.dto.DeliveryDto;
import ru.yandex.practicum.dto.DeliveryState;
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

    public DeliveryDto saveDelivery(DeliveryDto dto) {
        UUID deliveryId = dto.getDeliveryId();
        UUID orderId = dto.getOrderId();

        if (orderId == null) {
            throw new IllegalArgumentException("orderId обязателен");
        }

        Delivery entity;

        // Если ID есть — ищем и обновляем, иначе создаём новую
        if (deliveryId != null) {
            entity = deliveryRepository.findById(deliveryId)
                    .orElseThrow(() -> new IllegalArgumentException("Доставка не найдена: " + deliveryId));
            log.info("Обновление доставки с ID {}", deliveryId);
        } else {
            entity = new Delivery();
            entity.setId(UUID.randomUUID());
            // По ТЗ при создании ставим CREATED, даже если в DTO что-то другое
            entity.setDeliveryState(DeliveryState.CREATED);
            log.info("Создание новой доставки с ID {}", entity.getId());
        }

        // Заполняем адреса
        fillAddress(entity, dto.getFromAddress(), true);
        fillAddress(entity, dto.getToAddress(), false);

        entity.setOrderId(orderId);

        // deliveryState: если это создание — всегда CREATED. Если обновление — можно взять из DTO
        if (dto.getDeliveryState() != null && deliveryId != null) {
            entity.setDeliveryState(dto.getDeliveryState());
        }

        Delivery saved = deliveryRepository.save(entity);
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
