package com.brainbyte.easy_maintenance.infrastructure.notification.repository;

import com.brainbyte.easy_maintenance.infrastructure.notification.domain.BusinessWhatsAppDispatch;
import com.brainbyte.easy_maintenance.infrastructure.notification.enums.BusinessWhatsAppDispatchStatus;
import com.brainbyte.easy_maintenance.infrastructure.notification.enums.NotificationEventType;
import com.brainbyte.easy_maintenance.infrastructure.notification.enums.NotificationReferenceType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface BusinessWhatsAppDispatchRepository extends JpaRepository<BusinessWhatsAppDispatch, Long> {

    Optional<BusinessWhatsAppDispatch> findByOrganizationCodeAndEventTypeAndReferenceTypeAndReferenceIdAndDueDateAndDaysOffset(
            String organizationCode,
            NotificationEventType eventType,
            NotificationReferenceType referenceType,
            Long referenceId,
            LocalDate dueDate,
            int daysOffset);

    List<BusinessWhatsAppDispatch> findAllByStatus(BusinessWhatsAppDispatchStatus status);

    Optional<BusinessWhatsAppDispatch> findByWamid(String wamid);

    @Query("SELECT COUNT(d) FROM BusinessWhatsAppDispatch d " +
            "WHERE d.organizationCode = :orgCode " +
            "AND d.status = 'SENT' " +
            "AND d.sentAt >= :startOfMonth")
    long countSentInMonth(@Param("orgCode") String organizationCode, @Param("startOfMonth") Instant startOfMonth);

    @Query("SELECT COUNT(d) FROM BusinessWhatsAppDispatch d " +
            "WHERE d.recipientPhone = :phone " +
            "AND d.status = 'SENT' " +
            "AND d.sentAt >= :startOfDay")
    long countSentToPhoneSince(@Param("phone") String phone, @Param("startOfDay") Instant startOfDay);
}
