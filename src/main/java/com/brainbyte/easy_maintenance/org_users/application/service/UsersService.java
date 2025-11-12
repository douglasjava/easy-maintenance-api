package com.brainbyte.easy_maintenance.org_users.application.service;

import com.brainbyte.easy_maintenance.commons.dto.PageResponse;
import com.brainbyte.easy_maintenance.commons.exceptions.ConflictException;
import com.brainbyte.easy_maintenance.commons.exceptions.NotFoundException;
import com.brainbyte.easy_maintenance.org_users.application.dto.UserDTO;
import com.brainbyte.easy_maintenance.org_users.domain.User;
import com.brainbyte.easy_maintenance.org_users.infrastructure.persistence.UserRepository;
import com.brainbyte.easy_maintenance.org_users.mapper.IUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.Instant;

import static java.util.Objects.nonNull;
import static org.springframework.util.StringUtils.hasLength;

@Slf4j
@Service
@RequiredArgsConstructor
public class UsersService {

  private final OrganizationsService organizationsService;
  private final UserRepository repository;

  public UserDTO.UserResponse createUser(UserDTO.CreateUserRequest request, String orgCode) {
    log.info("Creating user {} - orgId: {}", request.email(), orgCode);

    if (repository.existsByOrganizationCode(orgCode)) {
      throw new ConflictException(String.format("Organization with code %s already exists for user %s", orgCode,
              request.email()));
    }

    var user = IUserMapper.INSTANCE.toUser(request, orgCode);
    user = repository.save(user);

    return IUserMapper.INSTANCE.toUserResponse(user);

  }

  public UserDTO.UserResponse findById(Long id, String orgId) {

    log.info("Getting user with id {}", id);
    return repository.findByOrganizationCodeAndId(orgId, id).map(IUserMapper.INSTANCE::toUserResponse)
            .orElseThrow(() -> new NotFoundException(String.format("User with id %s not found", id)));

  }

  public UserDTO.UserResponse updateUser(Long id, UserDTO.UpdateUserRequest request, String orgId) {
    log.info("Updating user with id {}", id);

    var user = repository.findByOrganizationCodeAndId(orgId, id).orElseThrow(() -> new NotFoundException(
            String.format("User with id %s not found", id)));

    if (!orgId.equals(user.getOrganizationCode())) {
      throw new ConflictException(String.format("User with id %s does not belong to organization %s", id, orgId));
    }

    if (hasLength(request.name())) {
      user.setName(request.name());
    }
    if (nonNull(request.role())) {
      user.setRole(request.role());
    }
    if (nonNull(request.status())) {
      user.setStatus(request.status());
    }
    user.setUpdatedAt(Instant.now());

    var updatedUser = repository.save(user);

    return IUserMapper.INSTANCE.toUserResponse(updatedUser);

  }

  public PageResponse<UserDTO.UserResponse> listAll(String orgId, Pageable pageable) {

    log.info("Listing all users by organization id: {}", orgId);

    var userExample = User.builder().organizationCode(orgId).build();
    var example = Example.of(userExample);

    Page<UserDTO.UserResponse> page =
            repository.findAll(example, pageable).map(IUserMapper.INSTANCE::toUserResponse);

    return PageResponse.of(page);


  }

}
