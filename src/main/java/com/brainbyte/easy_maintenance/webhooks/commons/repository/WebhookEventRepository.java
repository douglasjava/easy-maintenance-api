package com.brainbyte.easy_maintenance.webhooks.commons.repository;

import com.brainbyte.easy_maintenance.webhooks.commons.domain.WebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface WebhookEventRepository extends JpaRepository<WebhookEvent, Long> {

    Optional<WebhookEvent> findByProviderEventId(String providerEventId);

}
