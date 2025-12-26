package com.brainbyte.easy_maintenance.supplier.infrastructure.web;

import com.brainbyte.easy_maintenance.supplier.application.service.SupplierSearchService;
import com.brainbyte.easy_maintenance.supplier.application.dto.NearbySuppliersRequest;
import com.brainbyte.easy_maintenance.supplier.application.dto.NearbySuppliersResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

@RestController
@RequiredArgsConstructor
@RequestMapping("/easy-maintenance/api/v1/suppliers")
public class SuppliersController {

  private final SupplierSearchService service;

  @PostMapping("/nearby")
  public NearbySuppliersResponse nearby(@RequestBody @Valid NearbySuppliersRequest req) {
    return service.searchNearby(req);
  }

}

