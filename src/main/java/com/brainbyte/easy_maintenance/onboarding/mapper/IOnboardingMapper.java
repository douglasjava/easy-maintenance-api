package com.brainbyte.easy_maintenance.onboarding.mapper;

import com.brainbyte.easy_maintenance.billing.application.dto.OrganizationSubscriptionDTO;
import com.brainbyte.easy_maintenance.billing.domain.BillingAccount;
import com.brainbyte.easy_maintenance.onboarding.application.dto.OnboardingDTO;
import com.brainbyte.easy_maintenance.org_users.application.dto.OrganizationDTO;
import com.brainbyte.easy_maintenance.org_users.domain.User;
import com.brainbyte.easy_maintenance.payment.application.dto.CustomerDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

@Mapper(componentModel = "spring")
public interface IOnboardingMapper {

    IOnboardingMapper INSTANCE = Mappers.getMapper(IOnboardingMapper.class);

    @Mapping(target = "email", source = "billingEmail")
    CustomerDTO toCustomerDTO(BillingAccount account);

    OrganizationDTO.CreateOrganizationRequest toCreateOrganizationRequest(OnboardingDTO.AccountOrganizationRequest request);

    default OrganizationSubscriptionDTO.UpdateSubscriptionRequest toupdateSubscriptionRequest(
            OnboardingDTO.AccountOrganizationRequest request, User user) {

        return new OrganizationSubscriptionDTO.UpdateSubscriptionRequest(
                user.getId(),
                request.planCode(),
                request.status(),
                request.currentPeriodStart(),
                request.currentPeriodEnd(),
                request.trialEndsAt()
        );
    }

}
