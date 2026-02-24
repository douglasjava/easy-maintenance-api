package com.brainbyte.easy_maintenance.billing.application.service;

import com.brainbyte.easy_maintenance.billing.application.dto.OrganizationSubscriptionDTO;
import com.brainbyte.easy_maintenance.billing.application.dto.UserSubscriptionDTO;
import com.brainbyte.easy_maintenance.billing.domain.UserSubscription;
import com.brainbyte.easy_maintenance.billing.domain.enums.SubscriptionStatus;
import com.brainbyte.easy_maintenance.billing.infrastructure.persistence.BillingPlanRepository;
import com.brainbyte.easy_maintenance.billing.infrastructure.persistence.UserSubscriptionRepository;
import com.brainbyte.easy_maintenance.billing.mapper.IBillingMapper;
import com.brainbyte.easy_maintenance.commons.exceptions.NotFoundException;
import com.brainbyte.easy_maintenance.infrastructure.audit.AuditAction;
import com.brainbyte.easy_maintenance.infrastructure.audit.AuditService;
import com.brainbyte.easy_maintenance.infrastructure.observability.service.BusinessMetricsService;
import com.brainbyte.easy_maintenance.org_users.infrastructure.persistence.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserSubscriptionService {

    private final UserSubscriptionRepository repository;
    private final UserRepository userRepository;
    private final BillingPlanRepository planRepository;
    private final BusinessMetricsService businessMetricsService;
    private final AuditService auditService;

    @Transactional
    public UserSubscriptionDTO.SubscriptionResponse updateOrCreate(Long userId, UserSubscriptionDTO.UpdateSubscriptionRequest request) {
        log.info("Criando ou atualizando assinatura para usuário {}", userId);

        var subscription = repository.findByUserId(userId)
                .orElseGet(() -> {
                    var user = userRepository.findById(userId)
                            .orElseThrow(() -> new NotFoundException("User not found: " + userId));
                    return UserSubscription.builder().user(user).build();
                });

        var plan = planRepository.findByCode(request.planCode())
                .orElseThrow(() -> new NotFoundException("Billing plan not found: " + request.planCode()));

        subscription.setPlan(plan);
        subscription.setStatus(request.status());
        subscription.setCurrentPeriodStart(request.currentPeriodStart());
        subscription.setCurrentPeriodEnd(request.currentPeriodEnd());
        subscription.setTrialEndsAt(request.trialEndsAt());

        var saved = repository.save(subscription);

        auditService.log("USER_SUBSCRIPTION", saved.getId().toString(), AuditAction.UPDATE, request);

        businessMetricsService.counter("billing.user_subscription.created", "plan", request.planCode());
        if (request.status() == SubscriptionStatus.CANCELED) {
            businessMetricsService.counter("billing.user_subscription.canceled", "plan", request.planCode());
        }

        return IBillingMapper.INSTANCE.toUserSubscriptionResponse(saved);
    }

    public UserSubscriptionDTO.SubscriptionResponse findBySubscriptionUser(Long userId) {

        return repository.findByUserId(userId)
                .map(IBillingMapper.INSTANCE::toUserSubscriptionResponse)
                .orElseThrow(() -> new NotFoundException("Subscription not found for organization " + userId));
    }


    public List<UserSubscription> findAllByUserIdIn(List<Long> payerIds) {

        return repository.findAllByUserIdIn(payerIds);

    }

}
