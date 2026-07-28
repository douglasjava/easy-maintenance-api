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

  @Column(name = "created_by")
  private Long createdBy;

  @Column(name = "updated_by")
  private Long updatedBy;

  // TASK-137: cancelamento é a única forma de "corrigir" uma manutenção — nunca editar os campos
  // acima. cancelReason é o que sustenta o compliance: documenta por que foi cancelada.
  @Column(name = "cancelled_at")
  private Instant cancelledAt;

  @Column(name = "cancelled_by")
  private Long cancelledBy;

  @Column(name = "cancel_reason")
  private String cancelReason;

  // BUGFIX (QA manual, TASK-QA-MAN-011 C1): a UNIQUE (item_id, performed_at) antiga (V24) não
  // considerava deleted_at, então soft-delete deixava a linha cancelada "ocupando" o dia e
  // bloqueava registrar uma nova manutenção pro mesmo item/dia. Toda manutenção ATIVA usa 0 aqui
  // (preservando a unicidade original entre elas); cancel() seta para o próprio id ao cancelar,
  // que nunca colide com outra linha. Ver V85.
  @Column(name = "active_dedup_key")
  private long activeDedupKey;

}
