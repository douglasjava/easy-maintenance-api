package com.brainbyte.easy_maintenance.billing.application.service;

import com.brainbyte.easy_maintenance.billing.application.dto.BillingAdminDTO;
import com.brainbyte.easy_maintenance.billing.application.dto.OrganizationSubscriptionDTO;
import com.brainbyte.easy_maintenance.billing.domain.OrganizationSubscription;
import com.brainbyte.easy_maintenance.billing.domain.enums.SubscriptionStatus;
import com.brainbyte.easy_maintenance.billing.infrastructure.persistence.BillingPlanRepository;
import com.brainbyte.easy_maintenance.billing.infrastructure.persistence.OrganizationSubscriptionRepository;
import com.brainbyte.easy_maintenance.billing.infrastructure.persistence.UserSubscriptionRepository;
import com.brainbyte.easy_maintenance.billing.mapper.IBillingMapper;
import com.brainbyte.easy_maintenance.commons.exceptions.NotFoundException;
import com.brainbyte.easy_maintenance.org_users.infrastructure.persistence.OrganizationRepository;
import com.brainbyte.easy_maintenance.org_users.infrastructure.persistence.UserRepository;
import com.brainbyte.easy_maintenance.infrastructure.audit.AuditAction;
import com.brainbyte.easy_maintenance.infrastructure.audit.AuditService;
import com.brainbyte.easy_maintenance.infrastructure.observability.service.BusinessMetricsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrganizationSubscriptionService {

    private final OrganizationSubscriptionRepository repository;
    private final UserSubscriptionRepository userSubscriptionRepository;
    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;
    private final BillingPlanRepository planRepository;
    private final BusinessMetricsService businessMetricsService;
    private final AuditService auditService;

    public OrganizationSubscriptionDTO.SubscriptionResponse findByOrganizationCode(String orgCode) {
        return repository.findByOrganizationCode(orgCode)
                .map(IBillingMapper.INSTANCE::toSubscriptionResponse)
                .orElseThrow(() -> new NotFoundException("Subscription not found for organization " + orgCode));
    }

    @Transactional
    public OrganizationSubscriptionDTO.SubscriptionResponse updateOrCreate(String orgCode, OrganizationSubscriptionDTO.UpdateSubscriptionRequest request) {
        log.info("Criando ou atualizando um assinatura {}", orgCode);

        var subscription = repository.findByOrganizationCode(orgCode)
                .orElseGet(() -> {
                    var org = organizationRepository.findAllByCodeIn(Collections.singletonList(orgCode)).stream().findFirst()
                            .orElseThrow(() -> new NotFoundException("Organization not found: " + orgCode));
                    return OrganizationSubscription.builder().organization(org).build();
                });

        var payer = userRepository.findById(request.payerUserId())
                .orElseThrow(() -> new NotFoundException("Payer user not found: " + request.payerUserId()));
        
        var plan = planRepository.findByCode(request.planCode())
                .orElseThrow(() -> new NotFoundException("Billing plan not found: " + request.planCode()));

        subscription.setPayer(payer);
        subscription.setPlan(plan);
        subscription.setStatus(request.status());
        subscription.setCurrentPeriodStart(request.currentPeriodStart());
        subscription.setCurrentPeriodEnd(request.currentPeriodEnd());

        var saved = repository.save(subscription);

        auditService.log("SUBSCRIPTION", saved.getId().toString(), AuditAction.UPDATE, request);

        businessMetricsService.counter("billing.subscription.created", "plan", request.planCode());
        if (request.status() == SubscriptionStatus.CANCELED) {
            businessMetricsService.counter("billing.subscription.canceled", "plan", request.planCode());
        }

        return IBillingMapper.INSTANCE.toSubscriptionResponse(saved);
    }

    public List<OrganizationSubscriptionDTO.SubscriptionResponse> listSubscriptions(
            SubscriptionStatus status, String planCode, Long payerUserId, String query) {

        return repository.findAllFiltered(status, planCode, payerUserId, query).stream()
                .map(IBillingMapper.INSTANCE::toSubscriptionResponse)
                .collect(Collectors.toList());
    }

    public BillingAdminDTO.BillingCounters getCounters() {
        var totalPriceOrganization = repository.totalPriceOrganizationsActive();
        var totalPriceUser = userSubscriptionRepository.totalPriceUsersActive();
        var total = totalPriceOrganization + totalPriceUser;
        return new BillingAdminDTO.BillingCounters(
                repository.countTotalOrganizations(),
                repository.countTotalPayers(),
                total
        );
    }
}
