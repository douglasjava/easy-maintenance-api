package com.brainbyte.easy_maintenance.org_users.mapper;

import com.brainbyte.easy_maintenance.commons.validation.DocumentValidator;
import com.brainbyte.easy_maintenance.org_users.application.dto.OrganizationDTO;
import com.brainbyte.easy_maintenance.org_users.domain.Organization;
import com.brainbyte.easy_maintenance.org_users.domain.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.factory.Mappers;

@Mapper
public interface IOrganizationMapper {

    IOrganizationMapper INSTANCE = Mappers.getMapper(IOrganizationMapper.class);

    @Mapping(target = "responsibleUser", ignore = true)
    OrganizationDTO.OrganizationResponse toOrganizationResponse(Organization organization);

    OrganizationDTO.ResponsibleUser toResponsibleUser(User user);

    @Mapping(target = "doc", source = "doc", qualifiedByName = "sanitizeDocument")
    Organization toOrganization(OrganizationDTO.CreateOrganizationRequest request);

    @Named("sanitizeDocument")
    default String map(String document) {
        return DocumentValidator.sanitize(document);
    }

}
