package com.brainbyte.easy_maintenance.infrastructure.notification.domain;

import com.brainbyte.easy_maintenance.infrastructure.notification.enums.BusinessWhatsAppDispatchStatus;
import com.brainbyte.easy_maintenance.infrastructure.notification.enums.NotificationEventType;
import com.brainbyte.easy_maintenance.infrastructure.notification.enums.NotificationReferenceType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Auditoria/idempotência do envio de WhatsApp (TASK-130). Diferente de
 * {@code BusinessEmailDispatch}, tem uma constraint única de verdade (ver migration V81) porque
 * cada mensagem tem custo direto do provedor — reenvio duplicado não pode acontecer por engano.
 *
 * {@code daysOffset} entra na chave de dedup junto com {@code dueDate} porque um mesmo item
 * gera múltiplos checkpoints de OVERDUE (0/7/15/30 dias vencido) com o mesmo dueDate — sem
 * daysOffset na chave, o segundo checkpoint seria erroneamente tratado como duplicata do primeiro.
 */
@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "business_whatsapp_dispatches")
public class BusinessWhatsAppDispatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organization_code", nullable = false)
    private String organizationCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false)
    private NotificationEventType eventType;

    @Enumerated(EnumType.STRING)
    @Column(name = "reference_type", nullable = false)
    private NotificationReferenceType referenceType;

    @Column(name = "reference_id", nullable = false)
    private Long referenceId;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "days_offset", nullable = false)
    private int daysOffset;

    @Column(name = "recipient_phone")
    private String recipientPhone;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private BusinessWhatsAppDispatchStatus status;

    @Column(name = "wamid")
    private String wamid;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "sent_at")
    private Instant sentAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
