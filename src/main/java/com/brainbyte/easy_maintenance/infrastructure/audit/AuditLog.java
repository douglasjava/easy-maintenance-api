package com.brainbyte.easy_maintenance.infrastructure.audit;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;

@Entity
@Table(name = "audit_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "org_code")
    private String orgCode;

    @Column(name = "entity_type", nullable = false)
    private String entityType;

    @Column(name = "entity_id", nullable = false)
    private String entityId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false)
    private AuditAction action;

    @Column(name = "changed_by_user_id")
    private String changedByUserId;

    @Column(name = "changed_at")
    @Builder.Default
    private OffsetDateTime changedAt = OffsetDateTime.now();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "diff_json")
    private Object diffJson;

    @Column(name = "request_id")
    private String requestId;

    @Column(name = "ip")
    private String ip;

    @Column(name = "user_agent")
    private String userAgent;
}