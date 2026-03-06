package com.brainbyte.easy_maintenance.billing.mapper;

import com.brainbyte.easy_maintenance.billing.application.dto.*;
import com.brainbyte.easy_maintenance.billing.domain.*;
import com.brainbyte.easy_maintenance.billing.application.dto.BillingSubscriptionResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

@Mapper(componentModel = "spring")
public interface IBillingMapper {

    IBillingMapper INSTANCE = Mappers.getMapper(IBillingMapper.class);

    BillingPlanDTO.BillingPlanResponse toBillingPlanResponse(BillingPlan plan);

    @Mapping(target = "userId", source = "user.id")
    BillingAccountDTO.BillingAccountResponse toBillingAccountResponse(BillingAccount account);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    BillingPlan toBillingPlan(BillingPlanDTO.CreateBillingPlanRequest request);

    BillingSubscriptionResponse toBillingSubscriptionResponse(BillingSubscription subscription);
}
