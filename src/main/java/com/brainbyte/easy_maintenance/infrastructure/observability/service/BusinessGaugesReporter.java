package com.brainbyte.easy_maintenance.infrastructure.observability.service;

import com.brainbyte.easy_maintenance.billing.infrastructure.persistence.OrganizationSubscriptionRepository;
import com.brainbyte.easy_maintenance.org_users.infrastructure.persistence.OrganizationRepository;
import com.brainbyte.easy_maintenance.org_users.infrastructure.persistence.UserRepository;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class BusinessGaugesReporter {

    private final MeterRegistry meterRegistry;
    private final OrganizationSubscriptionRepository subscriptionRepository;
    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;

    @PostConstruct
    public void registerGauges() {
        meterRegistry.gauge("easy_revenue.total", subscriptionRepository, 
            repo -> repo.sumEstimatedMonthlyRevenue() != null ? repo.sumEstimatedMonthlyRevenue() / 100.0 : 0.0);

        meterRegistry.gauge("easy_active.subscriptions", subscriptionRepository,
                OrganizationSubscriptionRepository::countTotalOrganizations);

        meterRegistry.gauge("easy_organizations.active", organizationRepository,
                CrudRepository::count);

        meterRegistry.gauge("easy_users.active", userRepository,
                CrudRepository::count);
    }
}
