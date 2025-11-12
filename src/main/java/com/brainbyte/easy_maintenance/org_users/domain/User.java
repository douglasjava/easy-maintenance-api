package com.brainbyte.easy_maintenance.org_users.domain;

import com.brainbyte.easy_maintenance.org_users.domain.enums.Role;
import com.brainbyte.easy_maintenance.org_users.domain.enums.Status;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "users")
public class User {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "organization_code")
  private String organizationCode;

  private String email;
  private String name;

  @Enumerated(EnumType.STRING)
  private Role role;

  @Enumerated(EnumType.STRING)
  private Status status;

  @Column(name = "password_hash")
  private String passwordHash;

  @Column(name = "created_at")
  private Instant createdAt;

  @Column(name = "updated_at")
  private Instant updatedAt;

}
