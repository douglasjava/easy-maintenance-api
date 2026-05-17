package com.brainbyte.easy_maintenance.webhooks.commons.repository;

import com.brainbyte.easy_maintenance.webhooks.commons.domain.WebhookDlqEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface WebhookDlqRepository extends JpaRepository<WebhookDlqEntry, Long> {

    Optional<WebhookDlqEntry> findByProviderEventId(String providerEventId);

    Page<WebhookDlqEntry> findByReplayedAtIsNull(Pageable pageable);
}
