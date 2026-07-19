package com.brainbyte.easy_maintenance.infrastructure.notification.service;

import com.brainbyte.easy_maintenance.infrastructure.notification.dto.NotificationEvent;
import com.brainbyte.easy_maintenance.infrastructure.notification.enums.NotificationChannel;
import com.brainbyte.easy_maintenance.infrastructure.notification.enums.NotificationEventType;
import com.brainbyte.easy_maintenance.infrastructure.notification.enums.NotificationReferenceType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationChannelResolverTest {

    private NotificationChannelResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new NotificationChannelResolver();
        ReflectionTestUtils.setField(resolver, "urgentThresholdHours", 48);
    }

    private NotificationEvent event(NotificationEventType type, int daysOffset) {
        return NotificationEvent.builder()
                .organizationCode("ORG-001")
                .eventType(type)
                .referenceType(NotificationReferenceType.ITEM)
                .referenceId(1L)
                .referenceLabel("Extintor")
                .dueDate(LocalDate.now().plusDays(daysOffset))
                .daysOffset(daysOffset)
                .build();
    }

    @ParameterizedTest
    @CsvSource({
            "ITEM_NEAR_DUE, 30",
            "ITEM_NEAR_DUE, 15",
            "ITEM_NEAR_DUE, 7",
            "MAINTENANCE_NEAR_DUE, 30",
    })
    void nearDue_beyondUrgentThreshold_doesNotIncludeWhatsapp(String eventType, int daysOffset) {
        Set<NotificationChannel> channels = resolver.resolveChannels(
                event(NotificationEventType.valueOf(eventType), daysOffset));

        assertThat(channels).containsExactly(NotificationChannel.PUSH);
    }

    @ParameterizedTest
    @CsvSource({
            "ITEM_NEAR_DUE, 1",
            "MAINTENANCE_NEAR_DUE, 1",
    })
    void nearDue_withinUrgentThreshold_includesWhatsapp(String eventType, int daysOffset) {
        Set<NotificationChannel> channels = resolver.resolveChannels(
                event(NotificationEventType.valueOf(eventType), daysOffset));

        assertThat(channels).containsExactlyInAnyOrder(NotificationChannel.PUSH, NotificationChannel.WHATSAPP);
    }

    @ParameterizedTest
    @CsvSource({
            "ITEM_OVERDUE, 0",
            "ITEM_OVERDUE, 7",
            "ITEM_OVERDUE, 30",
            "MAINTENANCE_OVERDUE, 15",
    })
    void overdue_alwaysIncludesWhatsappRegardlessOfDaysOffset(String eventType, int daysOffset) {
        Set<NotificationChannel> channels = resolver.resolveChannels(
                event(NotificationEventType.valueOf(eventType), daysOffset));

        assertThat(channels).containsExactlyInAnyOrder(
                NotificationChannel.PUSH, NotificationChannel.EMAIL, NotificationChannel.WHATSAPP);
    }

    @Test
    void higherThreshold_widensNearDueEligibility() {
        ReflectionTestUtils.setField(resolver, "urgentThresholdHours", 24 * 8); // 8 dias

        Set<NotificationChannel> channels = resolver.resolveChannels(
                event(NotificationEventType.ITEM_NEAR_DUE, 7));

        assertThat(channels).contains(NotificationChannel.WHATSAPP);
    }

    @Test
    void unsupportedEventType_resolvesToEmptySet() {
        Set<NotificationChannel> channels = resolver.resolveChannels(
                event(NotificationEventType.PASSWORD_RESET, 0));

        assertThat(channels).isEmpty();
    }
}
