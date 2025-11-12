package com.brainbyte.easy_maintenance.catalog_norms.domain;

import com.brainbyte.easy_maintenance.assets.domain.enums.CustomPeriodUnit;
import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;

@Data
@Entity
@Table(name = "norms")
public class Norm {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "item_type")
  private String itemType;

  @Enumerated(EnumType.STRING)
  @Column(name = "period_unit")
  private CustomPeriodUnit periodUnit;

  @Column(name = "period_qty")
  private Integer periodQty;

  @Column(name = "tolerance_days")
  private Integer toleranceDays;

  @Column(name = "authority")
  private String authority;

  @Column(name = "doc_url")
  private String docUrl;

  @Column(name = "notes")
  private String notes;

  @Column(name = "created_at")
  private Instant createdAt;

  @Column(name = "updated_at")
  private Instant updatedAt;

}
