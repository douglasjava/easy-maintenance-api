package com.brainbyte.easy_maintenance.org_users.domain;

import com.brainbyte.easy_maintenance.org_users.domain.enums.Plan;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Entity
@Builder
@Table(name = "organizations")
@NoArgsConstructor
@AllArgsConstructor
public class Organization {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private String code;
  private String name;

  @Enumerated(EnumType.STRING)
  private Plan plan;

  private String city;
  private String doc;

  @Column(name = "created_at")
  private Instant createdAt;

  @Column(name = "updated_at")
  private Instant updatedAt;

}
