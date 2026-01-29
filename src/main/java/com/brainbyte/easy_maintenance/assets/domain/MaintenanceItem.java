package com.brainbyte.easy_maintenance.assets.domain;

import com.brainbyte.easy_maintenance.assets.domain.enums.CustomPeriodUnit;
import com.brainbyte.easy_maintenance.assets.domain.enums.ItemCategory;
import com.brainbyte.easy_maintenance.assets.domain.enums.ItemStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;

@Builder
@NoArgsConstructor
@AllArgsConstructor
@Data
@Entity
@Table(name = "maintenance_items")
public class MaintenanceItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organization_code")
    private String organizationCode;

    @Column(name = "item_type")
    private String itemType;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_category")
    private ItemCategory itemCategory;

    @Enumerated(EnumType.STRING)
    @Column(name = "custom_period_unit")
    private CustomPeriodUnit customPeriodUnit;

    @Column(name = "custom_period_qty")
    private Integer customPeriodQty;

    @Column(name = "criticality")
    private String criticality;

    @Column(name = "norm_id")
    private Long normId;

    @Column(name = "last_performed_at")
    private LocalDate lastPerformedAt;

    @Column(name = "next_due_at")
    private LocalDate nextDueAt;

    @Enumerated(EnumType.STRING)
    private ItemStatus status;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

}
