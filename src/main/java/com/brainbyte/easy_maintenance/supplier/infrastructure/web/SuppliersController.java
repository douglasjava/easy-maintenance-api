package com.brainbyte.easy_maintenance.supplier.infrastructure.web;

import com.brainbyte.easy_maintenance.supplier.application.service.SupplierSearchService;
import com.brainbyte.easy_maintenance.supplier.application.dto.NearbySuppliersRequest;
import com.brainbyte.easy_maintenance.supplier.application.dto.NearbySuppliersResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
@RequiredArgsConstructor
@RequestMapping("/easy-maintenance/api/v1/suppliers")
@Tag(name = "Fornecedores", description = "Gerenciamento e busca de fornecedores")
public class SuppliersController {

  private final SupplierSearchService service;

  @PostMapping("/nearby")
  @Operation(summary = "Busca fornecedores próximos com base na localização e especialidade")
  public NearbySuppliersResponse nearby(@RequestBody @Valid NearbySuppliersRequest req) {
    return service.searchNearby(req);
  }

}

