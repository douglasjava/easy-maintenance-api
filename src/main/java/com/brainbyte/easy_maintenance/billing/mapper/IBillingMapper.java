package com.brainbyte.easy_maintenance.billing.mapper;

import com.brainbyte.easy_maintenance.billing.application.dto.*;
import com.brainbyte.easy_maintenance.billing.domain.*;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

@Mapper(componentModel = "spring")
public interface IBillingMapper {

    IBillingMapper INSTANCE = Mappers.getMapper(IBillingMapper.class);

    BillingPlanDTO.BillingPlanResponse toBillingPlanResponse(BillingPlan plan);

    @Mapping(target = "userId", source = "user.id")
    BillingAccountDTO.BillingAccountResponse toBillingAccountResponse(BillingAccount account);

    @Mapping(target = "organizationId", source = "organization.id")
    @Mapping(target = "organizationCode", source = "organization.code")
    @Mapping(target = "organizationName", source = "organization.name")
    @Mapping(target = "payerUserId", source = "payer.id")
    @Mapping(target = "payerEmail", source = "payer.email")
    @Mapping(target = "planCode", source = "plan.code")
    @Mapping(target = "planName", source = "plan.name")
    @Mapping(target = "priceCents", source = "plan.priceCents")
    OrganizationSubscriptionDTO.SubscriptionResponse toSubscriptionResponse(OrganizationSubscription subscription);

    @Mapping(target = "payerUserId", source = "payer.id")
    InvoiceDTO.InvoiceResponse toInvoiceResponse(Invoice invoice);

    @Mapping(target = "organizationCode", source = "organization.code")
    InvoiceDTO.InvoiceItemResponse toInvoiceItemResponse(InvoiceItem item);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    BillingPlan toBillingPlan(BillingPlanDTO.CreateBillingPlanRequest request);
}
