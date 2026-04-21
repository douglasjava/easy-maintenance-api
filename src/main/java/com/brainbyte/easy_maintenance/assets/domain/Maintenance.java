package com.brainbyte.easy_maintenance.assets.domain;

import com.brainbyte.easy_maintenance.assets.domain.enums.MaintenanceType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.time.Instant;
import java.time.LocalDate;

@SQLDelete(sql = "UPDATE maintenances SET deleted_at = now() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
@Builder
@Data
@Entity
@Table(name = "maintenances")
@NoArgsConstructor
@AllArgsConstructor
public class Maintenance {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "item_id")
  private Long itemId;

  @Column(name = "performed_at")
  private LocalDate performedAt;

  @Enumerated(EnumType.STRING)
  @Column(name = "type")
  private MaintenanceType type;

  @Column(name = "performed_by")
  private String performedBy;

  @Column(name = "cost_cents")
  private Integer costCents;

  @Column(name = "next_due_at")
  private LocalDate nextDueAt;

  @Column(name = "created_at")
  private Instant createdAt;

  @Column(name = "deleted_at")
  private Instant deletedAt;

}
