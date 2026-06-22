package com.brainbyte.easy_maintenance.org_users.application.service;

import com.brainbyte.easy_maintenance.affiliates.application.service.AffiliateService;
import com.brainbyte.easy_maintenance.affiliates.domain.Affiliate;
import com.brainbyte.easy_maintenance.affiliates.domain.AffiliateStatus;
import com.brainbyte.easy_maintenance.ai.application.dto.CompanyType;
import com.brainbyte.easy_maintenance.billing.infrastructure.persistence.BillingAccountRepository;
import com.brainbyte.easy_maintenance.billing.application.service.BillingSubscriptionService;
import com.brainbyte.easy_maintenance.billing.infrastructure.persistence.BillingPlanRepository;
import com.brainbyte.easy_maintenance.billing.infrastructure.persistence.BillingSubscriptionItemRepository;
import com.brainbyte.easy_maintenance.org_users.application.dto.OrganizationDTO;
import com.brainbyte.easy_maintenance.org_users.domain.Organization;
import com.brainbyte.easy_maintenance.org_users.infrastructure.persistence.OrganizationRepository;
import com.brainbyte.easy_maintenance.org_users.infrastructure.persistence.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrganizationReferralAutoMatchTest {

    @Mock OrganizationRepository repository;
    @Mock BillingSubscriptionItemRepository billingSubscriptionItemRepository;
    @Mock BillingSubscriptionService billingSubscriptionService;
    @Mock BillingPlanRepository billingPlanRepository;
    @Mock BillingAccountRepository billingAccountRepository;
    @Mock UserRepository userRepository;
    @Mock AffiliateService affiliateService;

    @InjectMocks OrganizationsService service;

    @Test
    void create_savesExplicitReferralCode_whenProvided() {
        when(repository.existsByCode("ORG001")).thenReturn(false);
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        OrganizationDTO.CreateOrganizationRequest req = new OrganizationDTO.CreateOrganizationRequest(
                "ORG001", "Org 1", null, null, null, null, null, null, null, null,
                "12.345.678/0001-90", CompanyType.CONDOMINIUM, "ABC123", null);

        service.create(req);

        ArgumentCaptor<Organization> captor = ArgumentCaptor.forClass(Organization.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getReferralCode()).isEqualTo("ABC123");
        verifyNoInteractions(affiliateService);
    }

    @Test
    void create_autoMatchesReferralCode_whenUserEmailMatchesLead() {
        when(repository.existsByCode("ORG002")).thenReturn(false);
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        Affiliate affiliate = Affiliate.builder()
                .id(1L).code("XYZ999").name("Ana")
                .commissionRate(new BigDecimal("0.20"))
                .status(AffiliateStatus.ACTIVE).build();
        when(affiliateService.suggestForEmail("prospect@test.com"))
                .thenReturn(Optional.of(affiliate));

        OrganizationDTO.CreateOrganizationRequest req = new OrganizationDTO.CreateOrganizationRequest(
                "ORG002", "Org 2", null, null, null, null, null, null, null, null,
                "12.345.678/0001-90", CompanyType.CONDOMINIUM, null, "prospect@test.com");

        service.create(req);

        ArgumentCaptor<Organization> captor = ArgumentCaptor.forClass(Organization.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getReferralCode()).isEqualTo("XYZ999");
    }

    @Test
    void create_savesNullReferralCode_whenNoMatchAndNoExplicitCode() {
        when(repository.existsByCode("ORG003")).thenReturn(false);
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(affiliateService.suggestForEmail("organic@test.com"))
                .thenReturn(Optional.empty());

        OrganizationDTO.CreateOrganizationRequest req = new OrganizationDTO.CreateOrganizationRequest(
                "ORG003", "Org 3", null, null, null, null, null, null, null, null,
                "12.345.678/0001-90", CompanyType.CONDOMINIUM, null, "organic@test.com");

        service.create(req);

        ArgumentCaptor<Organization> captor = ArgumentCaptor.forClass(Organization.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getReferralCode()).isNull();
    }

    @Test
    void create_savesNullReferralCode_whenNoEmailProvided() {
        when(repository.existsByCode("ORG004")).thenReturn(false);
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        OrganizationDTO.CreateOrganizationRequest req = new OrganizationDTO.CreateOrganizationRequest(
                "ORG004", "Org 4", null, null, null, null, null, null, null, null,
                "12.345.678/0001-90", CompanyType.CONDOMINIUM, null, null);

        service.create(req);

        ArgumentCaptor<Organization> captor = ArgumentCaptor.forClass(Organization.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getReferralCode()).isNull();
        verifyNoInteractions(affiliateService);
    }
}
