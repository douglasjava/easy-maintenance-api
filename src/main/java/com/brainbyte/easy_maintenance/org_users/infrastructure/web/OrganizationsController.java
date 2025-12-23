package com.brainbyte.easy_maintenance.org_users.infrastructure.web;

import com.brainbyte.easy_maintenance.commons.dto.PageResponse;
import com.brainbyte.easy_maintenance.kernel.tenant.RequireTenant;
import com.brainbyte.easy_maintenance.org_users.application.dto.OrganizationDTO.CreateOrganizationRequest;
import com.brainbyte.easy_maintenance.org_users.application.dto.OrganizationDTO.OrganizationResponse;
import com.brainbyte.easy_maintenance.org_users.application.dto.OrganizationDTO.UpdateOrganizationRequest;
import com.brainbyte.easy_maintenance.org_users.application.service.OrganizationsService;
import com.brainbyte.easy_maintenance.shared.web.openapi.PageableAsQueryParam;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/easy-maintenance/api/v1/organizations")
public class OrganizationsController {

  private final OrganizationsService service;

  @PostMapping
  public OrganizationResponse create(@Valid @RequestBody CreateOrganizationRequest request) {
    return service.create(request);
  }

  @GetMapping("/{id}")
  public OrganizationResponse getById(@PathVariable Long id) {
    return service.getById(id);
  }

  @GetMapping
  @RequireTenant
  @PageableAsQueryParam
  public PageResponse<OrganizationResponse> listAll(@Parameter(hidden = true) Pageable pageable) {
    return service.listAll(pageable);
  }

  @PatchMapping("/{id}")
  @RequireTenant
  public OrganizationResponse update(@PathVariable Long id, @Valid @RequestBody UpdateOrganizationRequest req) {
    return service.update(id, req);
  }

}
