package com.brainbyte.easy_maintenance.billing.mapper;

import com.brainbyte.easy_maintenance.billing.application.dto.response.BillingSubscriptionResponse;
import com.brainbyte.easy_maintenance.billing.domain.BillingSubscriptionItem;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

@Mapper(componentModel = "spring")
public interface IBillingSubscriptionItems {

    IBillingSubscriptionItems INSTANCE = Mappers.getMapper(IBillingSubscriptionItems.class);

    @Mapping(source = "plan.code", target = "planCode")
    @Mapping(source = "plan.name", target = "planName")
    @Mapping(source = "nextPlan.code", target = "nextPlanCode")
    @Mapping(source = "billingSubscription.status", target = "status")
    @Mapping(source = "billingSubscription.currentPeriodStart", target = "currentPeriodStart")
    @Mapping(source = "billingSubscription.currentPeriodEnd", target = "currentPeriodEnd")
    BillingSubscriptionResponse.SubscriptionItemResponse toResponse(BillingSubscriptionItem item);

}
