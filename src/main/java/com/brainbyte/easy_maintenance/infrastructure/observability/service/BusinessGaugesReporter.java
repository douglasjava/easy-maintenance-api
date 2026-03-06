package com.brainbyte.easy_maintenance.infrastructure.observability.service;

import com.brainbyte.easy_maintenance.billing.infrastructure.persistence.BillingSubscriptionRepository;
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
    private final BillingSubscriptionRepository billingSubscriptionRepository;
    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;

    @PostConstruct
    public void registerGauges() {

        meterRegistry.gauge("easy_active.subscriptions", billingSubscriptionRepository,
                BillingSubscriptionRepository::count);

        meterRegistry.gauge("easy_organizations.active", organizationRepository,
                CrudRepository::count);

        meterRegistry.gauge("easy_users.active", userRepository,
                CrudRepository::count);

    }

}
