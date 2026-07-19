package com.brainbyte.easy_maintenance.infrastructure.notification.repository;

import com.brainbyte.easy_maintenance.infrastructure.notification.domain.BusinessWhatsAppDispatch;
import com.brainbyte.easy_maintenance.infrastructure.notification.enums.NotificationEventType;
import com.brainbyte.easy_maintenance.infrastructure.notification.enums.NotificationReferenceType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
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
}
