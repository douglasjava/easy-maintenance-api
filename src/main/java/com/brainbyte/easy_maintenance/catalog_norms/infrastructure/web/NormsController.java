package com.brainbyte.easy_maintenance.catalog_norms.infrastructure.web;

import com.brainbyte.easy_maintenance.catalog_norms.application.dto.NormDTO;
import com.brainbyte.easy_maintenance.catalog_norms.application.service.NormService;
import com.brainbyte.easy_maintenance.kernel.tenant.RequireTenant;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/easy-maintenance/api/v1/norms")
public class NormsController {

  private final NormService service;

  @GetMapping("/item-type")
  @RequireTenant
  public List<NormDTO.NormResponse> findByItemType(@RequestParam String itemType) {
    return service.findByItemType(itemType);
  }

  @GetMapping
  @RequireTenant
  public List<NormDTO.NormResponse> findAll() {
    return service.findAll();
  }

}
