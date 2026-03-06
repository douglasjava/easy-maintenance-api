package com.brainbyte.easy_maintenance.billing.application.service;

import com.brainbyte.easy_maintenance.assets.infrastructure.persistence.MaintenanceItemRepository;
import com.brainbyte.easy_maintenance.billing.domain.BillingPlan;
import com.brainbyte.easy_maintenance.billing.domain.BillingSubscription;
import com.brainbyte.easy_maintenance.billing.domain.BillingSubscriptionItem;
import com.brainbyte.easy_maintenance.billing.domain.BillingSubscriptionItemSourceType;
import com.brainbyte.easy_maintenance.billing.infrastructure.persistence.*;
import com.brainbyte.easy_maintenance.commons.exceptions.NotFoundException;
import com.brainbyte.easy_maintenance.infrastructure.audit.AuditService;
import com.brainbyte.easy_maintenance.infrastructure.saas.client.AsaasClient;
import com.brainbyte.easy_maintenance.org_users.application.service.AuthenticationService;
import com.brainbyte.easy_maintenance.payment.infrastructure.persistence.PaymentRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class OrganizationPlanChangeService extends AbstractSubscriptionChangePlanService<String> {

    private final MaintenanceItemRepository maintenanceItemRepository;
    private final AuthenticationService authenticationService;

    public OrganizationPlanChangeService(
            BillingPlanRepository planRepository,
            ProrataCalculator prorataCalculator,
            InvoiceRepository invoiceRepository,
            PaymentRepository paymentRepository,
            AuditService auditService,
            ObjectMapper objectMapper,
            BillingSubscriptionRepository billingSubscriptionRepository,
            BillingSubscriptionItemRepository billingSubscriptionItemRepository,
            AsaasClient asaasClient,
            MaintenanceItemRepository maintenanceItemRepository,
            AuthenticationService authenticationService) {
        super(planRepository, prorataCalculator, invoiceRepository, paymentRepository, auditService, objectMapper,
                billingSubscriptionRepository, billingSubscriptionItemRepository, asaasClient);
        this.maintenanceItemRepository = maintenanceItemRepository;
        this.authenticationService = authenticationService;
    }

    @Override
    protected BillingSubscription findBillingSubscription(String orgCode) {
        Long userId = authenticationService.getCurrentUser().getId();
        return billingSubscriptionRepository.findByBillingAccountUserId(userId)
                .orElseThrow(() -> new NotFoundException(String.format("Usuário %s não tem assinatura ativa", userId)));
    }

    @Override
    protected BillingSubscriptionItem findSubscriptionItem(BillingSubscription subscription, String orgCode) {
        return subscription.getItems().stream()
                .filter(item -> item.getSourceType() == BillingSubscriptionItemSourceType.ORGANIZATION && item.getSourceId().equals(orgCode))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Item de assinatura de organização não encontrado para: " + orgCode));
    }

    @Override
    protected void validateDowngradeLimits(String orgCode, BillingPlan newPlan) {
        long assetCount = maintenanceItemRepository.countByOrganizationCode(orgCode);
        if (assetCount > newPlan.getMaxAssets()) {
            throw new com.brainbyte.easy_maintenance.commons.exceptions.RuleException(
                    String.format("O plano %s permite apenas %d ativos. A organização %s possui %d.",
                            newPlan.getName(), newPlan.getMaxAssets(), orgCode, assetCount));
        }
    }

    @Override
    protected void setPendingPlan(BillingSubscription subscription, BillingSubscriptionItem item, BillingPlan newPlan) {
        subscription.setNextPlanOrg(newPlan);
        item.setNextPlan(newPlan);
    }

    @Override
    protected void setNextPlan(BillingSubscription subscription, BillingSubscriptionItem item, BillingPlan newPlan) {
        subscription.setNextPlanOrg(newPlan);
        subscription.setPlanChangeEffectiveAt(subscription.getCurrentPeriodEnd());
        item.setNextPlan(newPlan);
    }
}
