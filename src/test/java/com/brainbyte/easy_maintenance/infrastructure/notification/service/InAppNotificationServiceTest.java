package com.brainbyte.easy_maintenance.infrastructure.notification.service;

import com.brainbyte.easy_maintenance.commons.exceptions.NotFoundException;
import com.brainbyte.easy_maintenance.infrastructure.notification.domain.InAppNotification;
import com.brainbyte.easy_maintenance.infrastructure.notification.dto.InAppNotificationResponse;
import com.brainbyte.easy_maintenance.infrastructure.notification.enums.InAppNotificationType;
import com.brainbyte.easy_maintenance.infrastructure.notification.repository.InAppNotificationRepository;
import com.brainbyte.easy_maintenance.kernel.tenant.TenantContext;
import com.brainbyte.easy_maintenance.org_users.domain.User;
import com.brainbyte.easy_maintenance.org_users.domain.UserOrganization;
import com.brainbyte.easy_maintenance.org_users.infrastructure.persistence.UserOrganizationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InAppNotificationServiceTest {

    @Mock
    private InAppNotificationRepository repository;

    @Mock
    private UserOrganizationRepository userOrganizationRepository;

    @InjectMocks
    private InAppNotificationService service;

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    // -----------------------------------------------------------------------
    // listForUser — fallback (sem org no contexto)
    // -----------------------------------------------------------------------

    @Test
    void listForUser_returnsTop20MappedToResponse() {
        InAppNotification n = notification(1L, 42L, false, null);
        when(repository.findTop20ByUserIdOrderByCreatedAtDesc(42L)).thenReturn(List.of(n));

        List<InAppNotificationResponse> result = service.listForUser(42L);

        assertEquals(1, result.size());
        InAppNotificationResponse r = result.getFirst();
        assertEquals(1L, r.id());
        assertEquals("Título", r.title());
        assertEquals(InAppNotificationType.ITEM_DUE, r.type());
        assertFalse(r.read());
        assertNull(r.orgCode());
    }

    @Test
    void listForUser_marksReadNotificationAsRead() {
        InAppNotification n = notification(2L, 42L, true, null);
        when(repository.findTop20ByUserIdOrderByCreatedAtDesc(42L)).thenReturn(List.of(n));

        List<InAppNotificationResponse> result = service.listForUser(42L);
        assertTrue(result.getFirst().read());
    }

    // -----------------------------------------------------------------------
    // listForUser — org-scoped (com org no contexto)
    // -----------------------------------------------------------------------

    @Test
    void listForUser_withOrgContext_callsOrgScopedQuery() {
        TenantContext.set("org-1");
        InAppNotification n = notification(3L, 42L, false, "org-1");
        when(repository.findTop20ForUserAndOrg(eq(42L), eq("org-1"), any()))
                .thenReturn(List.of(n));

        List<InAppNotificationResponse> result = service.listForUser(42L);

        assertEquals(1, result.size());
        assertEquals("org-1", result.getFirst().orgCode());
        verify(repository).findTop20ForUserAndOrg(42L, "org-1", PageRequest.of(0, 20));
        verify(repository, never()).findTop20ByUserIdOrderByCreatedAtDesc(anyLong());
    }

    @Test
    void listForUser_withOrgContext_includesPersonalNotifications() {
        TenantContext.set("org-2");
        InAppNotification orgNotif = notification(4L, 10L, false, "org-2");
        InAppNotification personalNotif = notification(5L, 10L, false, null); // SUBSCRIPTION_BLOCKED
        when(repository.findTop20ForUserAndOrg(eq(10L), eq("org-2"), any()))
                .thenReturn(List.of(orgNotif, personalNotif));

        List<InAppNotificationResponse> result = service.listForUser(10L);

        assertEquals(2, result.size());
        assertTrue(result.stream().anyMatch(r -> "org-2".equals(r.orgCode())));
        assertTrue(result.stream().anyMatch(r -> r.orgCode() == null));
    }

    // -----------------------------------------------------------------------
    // countUnread
    // -----------------------------------------------------------------------

    @Test
    void countUnread_delegatesToRepository_whenNoOrgContext() {
        when(repository.countByUserIdAndReadAtIsNull(99L)).thenReturn(5L);
        assertEquals(5L, service.countUnread(99L));
        verify(repository).countByUserIdAndReadAtIsNull(99L);
    }

    @Test
    void countUnread_withOrgContext_callsOrgScopedQuery() {
        TenantContext.set("org-1");
        when(repository.countUnreadForUserAndOrg(99L, "org-1")).thenReturn(3L);

        assertEquals(3L, service.countUnread(99L));
        verify(repository).countUnreadForUserAndOrg(99L, "org-1");
        verify(repository, never()).countByUserIdAndReadAtIsNull(anyLong());
    }

    // -----------------------------------------------------------------------
    // markAsRead
    // -----------------------------------------------------------------------

    @Test
    void markAsRead_setsReadAt() {
        InAppNotification n = notification(1L, 42L, false, null);
        when(repository.findById(1L)).thenReturn(Optional.of(n));

        service.markAsRead(42L, 1L);

        assertNotNull(n.getReadAt());
        verify(repository).save(n);
    }

    @Test
    void markAsRead_skipsIfAlreadyRead() {
        InAppNotification n = notification(1L, 42L, true, null);
        when(repository.findById(1L)).thenReturn(Optional.of(n));

        service.markAsRead(42L, 1L);

        verify(repository, never()).save(any());
    }

    @Test
    void markAsRead_throwsIfNotFoundOrWrongUser() {
        InAppNotification n = notification(1L, 99L, false, null); // belongs to user 99
        when(repository.findById(1L)).thenReturn(Optional.of(n));

        assertThrows(NotFoundException.class, () -> service.markAsRead(42L, 1L));
    }

    // -----------------------------------------------------------------------
    // markAllRead
    // -----------------------------------------------------------------------

    @Test
    void markAllRead_delegatesToRepository_whenNoOrgContext() {
        service.markAllRead(42L);
        verify(repository).markAllReadByUserId(42L);
        verify(repository, never()).markAllReadForUserAndOrg(anyLong(), anyString());
    }

    @Test
    void markAllRead_withOrgContext_callsOrgScopedQuery() {
        TenantContext.set("org-1");
        service.markAllRead(42L);

        verify(repository).markAllReadForUserAndOrg(42L, "org-1");
        verify(repository, never()).markAllReadByUserId(anyLong());
    }

    // -----------------------------------------------------------------------
    // saveForOrg
    // -----------------------------------------------------------------------

    @Test
    void saveForOrg_createsOneNotificationPerUser() {
        UserOrganization uo1 = userOrg(10L);
        UserOrganization uo2 = userOrg(20L);
        when(userOrganizationRepository.findAllByOrganizationCode("org-1"))
                .thenReturn(List.of(uo1, uo2));

        service.saveForOrg("org-1", "Título", "Corpo",
                InAppNotificationType.ITEM_DUE, 7L, "");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<InAppNotification>> captor = ArgumentCaptor.forClass(List.class);
        verify(repository).saveAll(captor.capture());

        List<InAppNotification> saved = captor.getValue();
        assertEquals(2, saved.size());
        assertTrue(saved.stream().anyMatch(n -> n.getUserId() == 10L));
        assertTrue(saved.stream().anyMatch(n -> n.getUserId() == 20L));
        saved.forEach(n -> {
            assertEquals("org-1", n.getOrgCode());
            assertEquals("Título", n.getTitle());
            assertEquals(InAppNotificationType.ITEM_DUE, n.getType());
            assertEquals(7L, n.getReferenceId());
        });
    }

    @Test
    void saveForOrg_doesNothingWhenNoUsers() {
        when(userOrganizationRepository.findAllByOrganizationCode("org-empty"))
                .thenReturn(List.of());

        service.saveForOrg("org-empty", "X", "Y", InAppNotificationType.ITEM_DUE, null, "");

        verify(repository, never()).saveAll(any());
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private InAppNotification notification(Long id, Long userId, boolean read, String orgCode) {
        return InAppNotification.builder()
                .id(id)
                .userId(userId)
                .title("Título")
                .body("Corpo")
                .type(InAppNotificationType.ITEM_DUE)
                .referenceId(1L)
                .orgCode(orgCode)
                .readAt(read ? Instant.now() : null)
                .createdAt(Instant.now())
                .build();
    }

    private UserOrganization userOrg(Long userId) {
        User user = User.builder().id(userId).build();
        return UserOrganization.builder().user(user).build();
    }
}
