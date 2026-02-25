package com.brainbyte.easy_maintenance.webhooks.commons.service;

import com.brainbyte.easy_maintenance.webhooks.commons.domain.WebhookEvent;
import com.brainbyte.easy_maintenance.webhooks.commons.repository.WebhookEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class WebhookEventService {

    private final WebhookEventRepository repository;

    @Transactional
    public WebhookEvent save(WebhookEvent event) {
        return repository.save(event);
    }

    @Transactional(readOnly = true)
    public Optional<WebhookEvent> findByProviderEventId(String providerEventId) {
        return repository.findByProviderEventId(providerEventId);
    }

}
