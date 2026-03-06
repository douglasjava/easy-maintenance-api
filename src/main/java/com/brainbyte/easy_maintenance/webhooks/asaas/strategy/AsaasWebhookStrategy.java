package com.brainbyte.easy_maintenance.webhooks.asaas.strategy;

import com.brainbyte.easy_maintenance.infrastructure.saas.application.dto.AsaasDTO;

public interface AsaasWebhookStrategy {

    String getEventType();

    void handle(AsaasDTO.WebhookCheckoutEvent event);

}
