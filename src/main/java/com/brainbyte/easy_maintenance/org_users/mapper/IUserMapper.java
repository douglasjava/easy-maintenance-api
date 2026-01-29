package com.brainbyte.easy_maintenance.org_users.mapper;

import com.brainbyte.easy_maintenance.org_users.application.dto.UserDTO;
import com.brainbyte.easy_maintenance.org_users.domain.User;
import com.brainbyte.easy_maintenance.org_users.domain.UserOrganization;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import java.util.List;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring")
public interface IUserMapper {

  IUserMapper INSTANCE = Mappers.getMapper(IUserMapper.class);

  @Mapping(target = "organizationCodes", source = "organizations")
  UserDTO.UserResponse toUserResponse(User user);

  @Mapping(target = "organizationCodes", source = "organizations")
  UserDTO.LoginResponse toLoginResponse(User user);

  default List<String> mapOrganizations(List<UserOrganization> value) {
    if (value == null) return List.of();
    return value.stream()
            .map(UserOrganization::getOrganizationCode)
            .collect(Collectors.toList());
  }

  default User toUser(UserDTO.CreateUserRequest request, String orgCode) {

    return User.builder()
            .email(request.email())
            .name(request.name())
            .role(request.role())
            .status(request.status())
            .build();
  }

}
