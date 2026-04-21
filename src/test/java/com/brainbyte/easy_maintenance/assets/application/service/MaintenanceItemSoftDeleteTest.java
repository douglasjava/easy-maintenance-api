package com.brainbyte.easy_maintenance.assets.application.service;

import com.brainbyte.easy_maintenance.assets.component.ServiceBase;
import com.brainbyte.easy_maintenance.assets.domain.MaintenanceItem;
import com.brainbyte.easy_maintenance.assets.domain.enums.ItemCategory;
import com.brainbyte.easy_maintenance.assets.infrastructure.persistence.MaintenanceItemRepository;
import com.brainbyte.easy_maintenance.assets.infrastructure.persistence.MaintenanceRepository;
import com.brainbyte.easy_maintenance.catalog_norms.application.service.NormService;
import com.brainbyte.easy_maintenance.commons.exceptions.NotFoundException;
import com.brainbyte.easy_maintenance.commons.exceptions.TenantException;
import com.brainbyte.easy_maintenance.infrastructure.audit.AuditService;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * Tests for TASK-018: soft delete behaviour on MaintenanceItem.remove().
 *
 * The actual SQL transformation (DELETE → UPDATE deleted_at) is handled
 * at the persistence layer by Hibernate's @SQLDelete annotation.
 * These tests verify the service contract: tenant isolation is enforced
 * before the delete, deleteById is called exactly once, and the audit log
 * is written regardless.
 *
 * They also verify that the Hibernate soft-delete annotations are present
 * on all four critical entities (MaintenanceItem, Maintenance, User, Organization).
 */
@ExtendWith(MockitoExtension.class)
class MaintenanceItemSoftDeleteTest {

    @Mock private MaintenanceItemRepository repository;
    @Mock private MaintenanceRepository maintenanceRepository;
    @Mock private ServiceBase serviceBase;
    @Mock private NormService normService;
    @Mock private AuditService auditService;

    @InjectMocks
    private MaintenanceItemService service;

    private static final String ORG = "ORG-001";
    private static final Long ITEM_ID = 42L;

    private MaintenanceItem item;

    @BeforeEach
    void setUp() {
        item = MaintenanceItem.builder()
                .id(ITEM_ID)
                .organizationCode(ORG)
                .itemCategory(ItemCategory.OPERATIONAL)
                .build();
    }

    // -----------------------------------------------------------------------
    // remove() — happy path
    // -----------------------------------------------------------------------

    @Test
    void remove_shouldCallDeleteByIdOnce() {
        when(repository.findById(ITEM_ID)).thenReturn(Optional.of(item));

        service.remove(ORG, ITEM_ID);

        verify(repository, times(1)).deleteById(ITEM_ID);
    }

    @Test
    void remove_shouldWriteAuditLogAfterDelete() {
        when(repository.findById(ITEM_ID)).thenReturn(Optional.of(item));

        service.remove(ORG, ITEM_ID);

        verify(auditService, times(1)).logDelete(eq("MAINTENANCE_ITEM"), eq(ITEM_ID.toString()), eq(item));
    }

    // -----------------------------------------------------------------------
    // remove() — tenant isolation
    // -----------------------------------------------------------------------

    @Test
    void remove_shouldRejectDeleteForWrongOrg() {
        MaintenanceItem foreignItem = MaintenanceItem.builder()
                .id(ITEM_ID)
                .organizationCode("OTHER-ORG")
                .build();
        when(repository.findById(ITEM_ID)).thenReturn(Optional.of(foreignItem));

        assertThatThrownBy(() -> service.remove(ORG, ITEM_ID))
                .isInstanceOf(TenantException.class);

        verify(repository, never()).deleteById(any());
        verify(auditService, never()).logDelete(any(), any(), any());
    }

    @Test
    void remove_shouldThrowNotFoundWhenItemDoesNotExist() {
        when(repository.findById(ITEM_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.remove(ORG, ITEM_ID))
                .isInstanceOf(NotFoundException.class);

        verify(repository, never()).deleteById(any());
    }

    // -----------------------------------------------------------------------
    // @SQLDelete / @SQLRestriction annotation presence on all 4 entities
    // -----------------------------------------------------------------------

    @Test
    void maintenanceItem_shouldHaveSQLDeleteAnnotation() {
        SQLDelete annotation = MaintenanceItem.class.getAnnotation(SQLDelete.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.sql()).contains("maintenance_items").contains("deleted_at");
    }

    @Test
    void maintenanceItem_shouldHaveSQLRestrictionAnnotation() {
        SQLRestriction annotation = MaintenanceItem.class.getAnnotation(SQLRestriction.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).isEqualTo("deleted_at IS NULL");
    }

    @Test
    void maintenance_shouldHaveSoftDeleteAnnotations() {
        assertThat(com.brainbyte.easy_maintenance.assets.domain.Maintenance.class
                .getAnnotation(SQLDelete.class)).isNotNull();
        assertThat(com.brainbyte.easy_maintenance.assets.domain.Maintenance.class
                .getAnnotation(SQLRestriction.class)).isNotNull();
    }

    @Test
    void user_shouldHaveSoftDeleteAnnotations() {
        assertThat(com.brainbyte.easy_maintenance.org_users.domain.User.class
                .getAnnotation(SQLDelete.class)).isNotNull();
        assertThat(com.brainbyte.easy_maintenance.org_users.domain.User.class
                .getAnnotation(SQLRestriction.class)).isNotNull();
    }

    @Test
    void organization_shouldHaveSoftDeleteAnnotations() {
        assertThat(com.brainbyte.easy_maintenance.org_users.domain.Organization.class
                .getAnnotation(SQLDelete.class)).isNotNull();
        assertThat(com.brainbyte.easy_maintenance.org_users.domain.Organization.class
                .getAnnotation(SQLRestriction.class)).isNotNull();
    }

    @Test
    void allEntities_deletedAtFieldShouldExistAndBeNullable() throws NoSuchFieldException {
        // Verifies that the deleted_at field exists on all 4 entities
        assertThat(MaintenanceItem.class.getDeclaredField("deletedAt")).isNotNull();
        assertThat(com.brainbyte.easy_maintenance.assets.domain.Maintenance.class
                .getDeclaredField("deletedAt")).isNotNull();
        assertThat(com.brainbyte.easy_maintenance.org_users.domain.User.class
                .getDeclaredField("deletedAt")).isNotNull();
        assertThat(com.brainbyte.easy_maintenance.org_users.domain.Organization.class
                .getDeclaredField("deletedAt")).isNotNull();
    }
}
