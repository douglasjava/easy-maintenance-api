package com.brainbyte.easy_maintenance.org_users.mapper;

import com.brainbyte.easy_maintenance.org_users.application.dto.UserDTO;
import com.brainbyte.easy_maintenance.org_users.domain.User;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.time.Instant;

@Mapper(componentModel = "spring")
public interface IUserMapper {

  IUserMapper INSTANCE = Mappers.getMapper(IUserMapper.class);

  UserDTO.UserResponse toUserResponse(User user);

  default User toUser(UserDTO.CreateUserRequest request, String orgCode) {

    return User.builder()
            .organizationCode(orgCode)
            .email(request.email())
            .name(request.name())
            .role(request.role())
            .status(request.status())
            .passwordHash(request.passwordHash())
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();
  }

}
