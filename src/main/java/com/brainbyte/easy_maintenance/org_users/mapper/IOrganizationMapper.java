package com.brainbyte.easy_maintenance.org_users.mapper;

import com.brainbyte.easy_maintenance.org_users.application.dto.OrganizationDTO;
import com.brainbyte.easy_maintenance.org_users.domain.Organization;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

@Mapper
public interface IOrganizationMapper {

  IOrganizationMapper INSTANCE = Mappers.getMapper(IOrganizationMapper.class);

  OrganizationDTO.OrganizationResponse toOrganizationResponse(Organization organization);

  Organization toOrganization(OrganizationDTO.CreateOrganizationRequest request);

}
