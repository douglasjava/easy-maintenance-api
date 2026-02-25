package com.brainbyte.easy_maintenance.webhooks.asaas.service;

import com.brainbyte.easy_maintenance.infrastructure.saas.application.dto.AsaasDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AsaasWebhookService {

    public void processEvent(AsaasDTO.WebhookCheckoutEvent event) {
        if (event == null) {
            log.warn("Asaas webhook received null event");
            return;
        }
        log.info("[AsaasWebhook] Event id={} type={} date={} customer={} checkoutId={}",
                event.id(), event.event(), event.dateCreated(),
                event.checkout() != null ? event.checkout().customer() : null,
                event.checkout() != null ? event.checkout().id() : null);



    }
}
