package com.brainbyte.easy_maintenance.billing.application.service;

import com.brainbyte.easy_maintenance.assets.infrastructure.persistence.MaintenanceItemRepository;
import com.brainbyte.easy_maintenance.billing.domain.BillingPlan;
import com.brainbyte.easy_maintenance.billing.domain.BillingSubscription;
import com.brainbyte.easy_maintenance.billing.domain.BillingSubscriptionItem;
import com.brainbyte.easy_maintenance.billing.infrastructure.persistence.*;
import com.brainbyte.easy_maintenance.commons.exceptions.RuleException;
import com.brainbyte.easy_maintenance.infrastructure.audit.AuditService;
import com.brainbyte.easy_maintenance.infrastructure.saas.client.AsaasClient;
import com.brainbyte.easy_maintenance.infrastructure.saas.properties.AsaasProperties;
import com.brainbyte.easy_maintenance.payment.infrastructure.persistence.PaymentRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class OrganizationPlanChangeService extends AbstractSubscriptionChangePlanService<String> {

    private final MaintenanceItemRepository maintenanceItemRepository;

    public OrganizationPlanChangeService(BillingPlanRepository planRepository, ProrataCalculator prorataCalculator,
            InvoiceRepository invoiceRepository, PaymentRepository paymentRepository,
            AuditService auditService, ObjectMapper objectMapper,
            BillingSubscriptionRepository billingSubscriptionRepository,
            BillingSubscriptionItemRepository billingSubscriptionItemRepository,
            AsaasClient asaasClient, BillingPlanFeaturesHelper featuresHelper,
            MaintenanceItemRepository maintenanceItemRepository, AsaasProperties asaasProperties) {

        super(planRepository, prorataCalculator, invoiceRepository, paymentRepository, auditService, objectMapper,
                billingSubscriptionRepository, billingSubscriptionItemRepository, asaasClient, featuresHelper, asaasProperties);

        this.maintenanceItemRepository = maintenanceItemRepository;

    }


    @Override
    protected void validateDowngradeLimits(String orgCode, BillingPlan newPlan) {
        long assetCount = maintenanceItemRepository.countByOrganizationCode(orgCode);
        var features = featuresHelper.parse(newPlan);
        if (assetCount > features.getMaxItems()) {
            throw new RuleException(
                    String.format("O plano %s permite apenas %d ativos. A organização %s possui %d.",
                            newPlan.getName(), features.getMaxItems(), orgCode, assetCount));
        }
    }

}
