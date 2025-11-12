package com.brainbyte.easy_maintenance.assets.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;

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

  @Column(name = "issued_by")
  private String issuedBy;

  @Column(name = "certificate_number")
  private String certificateNumber;

  @Column(name = "certificate_valid_until")
  private LocalDate certificateValidUntil;

  @Column(name = "receipt_url")
  private String receiptUrl;

  @Column(name = "created_at")
  private Instant createdAt;

}
