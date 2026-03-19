package com.brainbyte.easy_maintenance.billing.application.service;

import com.brainbyte.easy_maintenance.billing.domain.BillingPlan;
import com.brainbyte.easy_maintenance.billing.domain.BillingSubscription;
import com.brainbyte.easy_maintenance.billing.domain.BillingSubscriptionItem;
import com.brainbyte.easy_maintenance.billing.infrastructure.persistence.*;
import com.brainbyte.easy_maintenance.commons.exceptions.RuleException;
import com.brainbyte.easy_maintenance.infrastructure.audit.AuditService;
import com.brainbyte.easy_maintenance.infrastructure.saas.client.AsaasClient;
import com.brainbyte.easy_maintenance.infrastructure.saas.properties.AsaasProperties;
import com.brainbyte.easy_maintenance.org_users.infrastructure.persistence.UserOrganizationRepository;
import com.brainbyte.easy_maintenance.payment.infrastructure.persistence.PaymentRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class UserPlanChangeService extends AbstractSubscriptionChangePlanService<Long> {

    private final UserOrganizationRepository userOrganizationRepository;

    public UserPlanChangeService(
            BillingPlanRepository planRepository, ProrataCalculator prorataCalculator,
            InvoiceRepository invoiceRepository, PaymentRepository paymentRepository,
            AuditService auditService, ObjectMapper objectMapper,
            BillingSubscriptionRepository billingSubscriptionRepository,
            BillingSubscriptionItemRepository billingSubscriptionItemRepository,
            AsaasClient asaasClient, BillingPlanFeaturesHelper featuresHelper,
            UserOrganizationRepository userOrganizationRepository, AsaasProperties asaasProperties) {
        
        super(planRepository, prorataCalculator, invoiceRepository,
                paymentRepository, auditService, objectMapper,
                billingSubscriptionRepository, billingSubscriptionItemRepository,
                asaasClient, featuresHelper, asaasProperties);
        
        this.userOrganizationRepository = userOrganizationRepository;
    }

    @Override
    protected void validateDowngradeLimits(Long userId, BillingPlan newPlan) {
        long organizationCount = userOrganizationRepository.countByUserId(userId);
        var features = featuresHelper.parse(newPlan);
        if (organizationCount > features.getMaxOrganizations()) {
            throw new RuleException(
                    String.format("O plano %s permite apenas %d organizações. Você possui %d.",
                            newPlan.getName(), features.getMaxOrganizations(), organizationCount));
        }
    }

}
