package com.brainbyte.easy_maintenance.billing.application.service;

import com.brainbyte.easy_maintenance.billing.application.dto.response.BillingSubscriptionResponse;
import com.brainbyte.easy_maintenance.billing.infrastructure.persistence.BillingSubscriptionItemRepository;
import com.brainbyte.easy_maintenance.billing.mapper.IBillingSubscriptionItems;
import com.brainbyte.easy_maintenance.commons.exceptions.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class BillingSubscriptionItemService {

    private final BillingSubscriptionItemRepository itemRepository;

    public BillingSubscriptionResponse.SubscriptionItemResponse findById(Long id) {
        log.info("Buscando item de assinatura {}", id);

        var subscriptionItemResponse = itemRepository.findByIdFetchDetails(id)
                .orElseThrow(() -> new NotFoundException(String.format("Item de assinatura %s não encontrado", id)));

        return IBillingSubscriptionItems.INSTANCE.toResponse(subscriptionItemResponse);

    }

}
