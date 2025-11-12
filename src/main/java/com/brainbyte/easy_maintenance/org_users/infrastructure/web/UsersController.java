package com.brainbyte.easy_maintenance.org_users.infrastructure.web;

import com.brainbyte.easy_maintenance.commons.dto.PageResponse;
import com.brainbyte.easy_maintenance.commons.exceptions.TenantException;
import com.brainbyte.easy_maintenance.kernel.tenant.RequireTenant;
import com.brainbyte.easy_maintenance.kernel.tenant.TenantContext;
import com.brainbyte.easy_maintenance.org_users.application.dto.UserDTO.CreateUserRequest;
import com.brainbyte.easy_maintenance.org_users.application.dto.UserDTO.UpdateUserRequest;
import com.brainbyte.easy_maintenance.org_users.application.dto.UserDTO.UserResponse;
import com.brainbyte.easy_maintenance.org_users.application.service.UsersService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/organizations/{orgId}/users")
public class UsersController {

  private final UsersService service;

  @PostMapping
  @RequireTenant
  public UserResponse create(@PathVariable String orgId, @Valid @RequestBody CreateUserRequest req) {

    assertTenantMatchesPath(orgId);

    return service.createUser(req, orgId);

  }

  @GetMapping
  @RequireTenant
  public PageResponse<UserResponse> list(@PathVariable String orgId, Pageable pageable) {
    assertTenantMatchesPath(orgId);

    return service.listAll(orgId, pageable);

  }

  @GetMapping("/{id}")
  @RequireTenant
  public UserResponse findById(@PathVariable String orgId, @PathVariable Long id) {
    assertTenantMatchesPath(orgId);

    return service.findById(id, orgId);

  }

  @PatchMapping("/{id}")
  @RequireTenant
  public UserResponse update(@PathVariable String orgId,
          @PathVariable Long id,
          @Valid @RequestBody UpdateUserRequest req) {

    assertTenantMatchesPath(orgId);

    return service.updateUser(id, req, orgId);

  }

  private void assertTenantMatchesPath(String orgId) {
    String tenant = TenantContext.get().orElseThrow(() -> new TenantException(HttpStatus.BAD_REQUEST, "X-Org-Id header is required"));
    if (!tenant.equals(orgId)) {
      throw new TenantException(HttpStatus.BAD_REQUEST, "X-Org-Id does not match path orgId");
    }
  }

}
