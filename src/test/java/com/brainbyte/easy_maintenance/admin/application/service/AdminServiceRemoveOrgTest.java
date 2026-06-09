package com.brainbyte.easy_maintenance.admin.application.service;

import com.brainbyte.easy_maintenance.billing.application.service.BillingSubscriptionService;
import com.brainbyte.easy_maintenance.billing.domain.BillingSubscriptionItem;
import com.brainbyte.easy_maintenance.billing.domain.BillingSubscriptionItemSourceType;
import com.brainbyte.easy_maintenance.billing.infrastructure.persistence.BillingSubscriptionItemRepository;
import com.brainbyte.easy_maintenance.infrastructure.mail.utils.EmailTemplateHelper;
import com.brainbyte.easy_maintenance.infrastructure.notification.service.CriticalEmailDispatchService;
import com.brainbyte.easy_maintenance.org_users.application.service.FirstAccessTokenService;
import com.brainbyte.easy_maintenance.org_users.application.service.OrganizationsService;
import com.brainbyte.easy_maintenance.org_users.application.service.UsersService;
import com.brainbyte.easy_maintenance.org_users.infrastructure.persistence.OrganizationRepository;
import com.brainbyte.easy_maintenance.org_users.infrastructure.persistence.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminServiceRemoveOrgTest {

    @Mock UsersService usersService;
    @Mock OrganizationsService organizationsService;
    @Mock FirstAccessTokenService firstAccessService;
    @Mock OrganizationRepository organizationRepository;
    @Mock UserRepository userRepository;
    @Mock BillingSubscriptionItemRepository billingSubscriptionItemRepository;
    @Mock BillingSubscriptionService billingSubscriptionService;
    @Mock CriticalEmailDispatchService criticalEmailDispatchService;
    @Mock EmailTemplateHelper emailTemplateHelper;

    @InjectMocks AdminService adminService;

    @Test
    void removeOrganizationFromUser_withActiveSubscription_schedulesItemCancellation() {
        Long userId = 1L;
        String orgCode = "ORG-001";
        Long itemId = 42L;

        BillingSubscriptionItem item = BillingSubscriptionItem.builder().id(itemId).build();
        when(billingSubscriptionItemRepository.findBySourceTypeAndSourceId(
                BillingSubscriptionItemSourceType.ORGANIZATION, orgCode))
                .thenReturn(Optional.of(item));

        adminService.removeOrganizationFromUser(userId, orgCode);

        verify(usersService).removeOrganization(userId, orgCode);
        verify(billingSubscriptionService).scheduleItemCancellation(itemId);
    }

    @Test
    void removeOrganizationFromUser_withoutSubscription_onlyUnlinks() {
        Long userId = 2L;
        String orgCode = "ORG-002";

        when(billingSubscriptionItemRepository.findBySourceTypeAndSourceId(
                BillingSubscriptionItemSourceType.ORGANIZATION, orgCode))
                .thenReturn(Optional.empty());

        adminService.removeOrganizationFromUser(userId, orgCode);

        verify(usersService).removeOrganization(userId, orgCode);
        verifyNoInteractions(billingSubscriptionService);
    }
}
