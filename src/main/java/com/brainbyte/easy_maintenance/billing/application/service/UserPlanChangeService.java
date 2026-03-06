package com.brainbyte.easy_maintenance.billing.application.service;

import com.brainbyte.easy_maintenance.billing.domain.BillingPlan;
import com.brainbyte.easy_maintenance.billing.domain.BillingSubscription;
import com.brainbyte.easy_maintenance.billing.domain.BillingSubscriptionItem;
import com.brainbyte.easy_maintenance.billing.domain.BillingSubscriptionItemSourceType;
import com.brainbyte.easy_maintenance.billing.infrastructure.persistence.*;
import com.brainbyte.easy_maintenance.commons.exceptions.NotFoundException;
import com.brainbyte.easy_maintenance.infrastructure.audit.AuditService;
import com.brainbyte.easy_maintenance.infrastructure.saas.client.AsaasClient;
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
            AsaasClient asaasClient, UserOrganizationRepository userOrganizationRepository) {

        super(planRepository, prorataCalculator, invoiceRepository,
                paymentRepository, auditService, objectMapper,
                billingSubscriptionRepository, billingSubscriptionItemRepository,
                asaasClient);

        this.userOrganizationRepository = userOrganizationRepository;

    }

    @Override
    protected BillingSubscription findBillingSubscription(Long userId) {
        return billingSubscriptionRepository.findByBillingAccountUserId(userId)
                .orElseThrow(() -> new NotFoundException(String.format("Usuário %s não tem assinatura", userId)));
    }

    @Override
    protected BillingSubscriptionItem findSubscriptionItem(BillingSubscription subscription, Long userId) {
        return subscription.getItems().stream()
                .filter(item -> item.getSourceType() == BillingSubscriptionItemSourceType.USER && item.getSourceId().equals(userId.toString()))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Item de assinatura de usuário não encontrado para o usuário: " + userId));
    }

    @Override
    protected void validateDowngradeLimits(Long userId, BillingPlan newPlan) {
        long organizationCount = userOrganizationRepository.countByUserId(userId);
        if (organizationCount > newPlan.getMaxOrganizations()) {
            throw new com.brainbyte.easy_maintenance.commons.exceptions.RuleException(
                    String.format("O plano %s permite apenas %d organizações. Você possui %d.",
                            newPlan.getName(), newPlan.getMaxOrganizations(), organizationCount));
        }
    }

    @Override
    protected void setPendingPlan(BillingSubscription subscription, BillingSubscriptionItem item, BillingPlan newPlan) {
        subscription.setNextPlanUser(newPlan);
        item.setNextPlan(newPlan);
    }

    @Override
    protected void setNextPlan(BillingSubscription subscription, BillingSubscriptionItem item, BillingPlan newPlan) {
        subscription.setNextPlanUser(newPlan);
        subscription.setPlanChangeEffectiveAt(subscription.getCurrentPeriodEnd());
        item.setNextPlan(newPlan);
    }

}
