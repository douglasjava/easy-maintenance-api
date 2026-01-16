package com.brainbyte.easy_maintenance.assets.domain;

import com.brainbyte.easy_maintenance.org_users.domain.enums.Status;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Builder
@Data
@Entity
@Table(name = "item_types")
@NoArgsConstructor
@AllArgsConstructor
public class ItemTypes {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", length = 50, nullable = false)
    private String name;

    @Column(name = "normalized_name", length = 50, nullable = false)
    private String normalizedName;

    @Enumerated(EnumType.STRING)
    private Status status;

    @Column(name = "created_at")
    private Instant createdAt;

}
