package com.brainbyte.easy_maintenance.billing.application.service;

import com.brainbyte.easy_maintenance.assets.infrastructure.persistence.MaintenanceItemRepository;
import com.brainbyte.easy_maintenance.billing.domain.BillingPlan;
import com.brainbyte.easy_maintenance.billing.infrastructure.persistence.*;
import com.brainbyte.easy_maintenance.commons.exceptions.RuleException;
import com.brainbyte.easy_maintenance.infrastructure.audit.AuditService;
import com.brainbyte.easy_maintenance.infrastructure.saas.client.AsaasClient;
import com.brainbyte.easy_maintenance.infrastructure.saas.properties.AsaasProperties;
import com.brainbyte.easy_maintenance.kernel.tenant.TenantContext;
import com.brainbyte.easy_maintenance.org_users.domain.Organization;
import com.brainbyte.easy_maintenance.org_users.infrastructure.persistence.OrganizationRepository;
import com.brainbyte.easy_maintenance.org_users.infrastructure.persistence.UserOrganizationRepository;
import com.brainbyte.easy_maintenance.payment.infrastructure.persistence.PaymentRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class UserPlanChangeService extends AbstractSubscriptionChangePlanService<Long> {

    private final UserOrganizationRepository userOrganizationRepository;
    private final OrganizationRepository organizationRepository;
    private final MaintenanceItemRepository maintenanceItemRepository;

    public UserPlanChangeService(
            BillingPlanRepository planRepository, ProrataCalculator prorataCalculator,
            InvoiceRepository invoiceRepository, PaymentRepository paymentRepository,
            AuditService auditService, ObjectMapper objectMapper,
            BillingSubscriptionRepository billingSubscriptionRepository,
            BillingSubscriptionItemRepository billingSubscriptionItemRepository,
            AsaasClient asaasClient, BillingPlanFeaturesHelper featuresHelper,
            UserOrganizationRepository userOrganizationRepository, AsaasProperties asaasProperties,
            OrganizationRepository organizationRepository, MaintenanceItemRepository maintenanceItemRepository) {

        super(planRepository, prorataCalculator, invoiceRepository,
                paymentRepository, auditService, objectMapper,
                billingSubscriptionRepository, billingSubscriptionItemRepository,
                asaasClient, featuresHelper, asaasProperties);

        this.userOrganizationRepository = userOrganizationRepository;
        this.organizationRepository = organizationRepository;
        this.maintenanceItemRepository = maintenanceItemRepository;
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

        // EPIC-014/TASK-112: maxItems é um pool compartilhado entre todas as organizações da conta.
        int maxItems = features.getMaxItems();
        if (maxItems > 0) {
            List<String> orgCodes = organizationRepository.findAllByUserId(userId).stream()
                    .map(Organization::getCode)
                    .toList();

            if (!orgCodes.isEmpty()) {
                // EPIC-014 bugfix: TenantFilterAspect zera a contagem de qualquer organização
                // que não seja a ativa na sessão — soma cross-org precisa do filtro desligado.
                long itemCount = TenantContext.runCrossOrg(() -> maintenanceItemRepository.countByOrganizationCodeIn(orgCodes));
                if (itemCount > maxItems) {
                    throw new RuleException(
                            String.format("O plano %s permite apenas %d itens no total, somando todas as suas organizações. Você possui %d.",
                                    newPlan.getName(), maxItems, itemCount));
                }
            }
        }
    }

}
