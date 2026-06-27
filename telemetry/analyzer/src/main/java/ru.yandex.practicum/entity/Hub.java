package ru.yandex.practicum.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Entity
@Table(name = "hubs")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Hub {
    @Id
    @Column(name = "hub_id", length = 64)
    private String hubId;

    @Column(name = "location")
    private String location;
}
