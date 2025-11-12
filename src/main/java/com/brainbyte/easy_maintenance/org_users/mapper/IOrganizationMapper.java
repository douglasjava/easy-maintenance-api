package com.brainbyte.easy_maintenance.org_users.mapper;

import com.brainbyte.easy_maintenance.org_users.application.dto.OrganizationDTO;
import com.brainbyte.easy_maintenance.org_users.domain.Organization;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.time.Instant;

@Mapper
public interface IOrganizationMapper {

  IOrganizationMapper INSTANCE = Mappers.getMapper(IOrganizationMapper.class);

  OrganizationDTO.OrganizationResponse toOrganizationResponse(Organization organization);

  default Organization toOrganization(OrganizationDTO.CreateOrganizationRequest request) {

    return Organization.builder()
            .code(request.code())
            .name(request.name())
            .plan(request.plan())
            .city(request.city())
            .doc(request.doc())
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
        .build();

  }


}
