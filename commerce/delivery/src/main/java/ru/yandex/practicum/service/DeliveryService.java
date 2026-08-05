package ru.yandex.practicum.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.yandex.practicum.dto.AddressDto;
import ru.yandex.practicum.dto.DeliveryDto;
import ru.yandex.practicum.dto.DeliveryState;
import ru.yandex.practicum.entity.Delivery;
import ru.yandex.practicum.repository.DeliveryRepository;

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
