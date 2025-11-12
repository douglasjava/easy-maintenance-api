package com.brainbyte.easy_maintenance.assets.mapper;

import com.brainbyte.easy_maintenance.assets.application.dto.MaintenanceResponse;
import com.brainbyte.easy_maintenance.assets.application.dto.RegisterMaintenanceRequest;
import com.brainbyte.easy_maintenance.assets.domain.Maintenance;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.time.Instant;
import java.util.UUID;

@Mapper(componentModel = "spring")
public interface IMaintenanceMapper {

  IMaintenanceMapper INSTANCE = Mappers.getMapper(IMaintenanceMapper.class);

  default Maintenance toMaintenance(RegisterMaintenanceRequest req, Long itemId) {

    Maintenance maintenance = new Maintenance();
    maintenance.setItemId(itemId);
    maintenance.setPerformedAt(req.performedAt());
    maintenance.setIssuedBy(req.issuedBy());
    maintenance.setCertificateNumber(req.certificateNumber());
    maintenance.setCertificateValidUntil(req.certificateValidUntil());
    maintenance.setReceiptUrl(req.receiptUrl());
    maintenance.setCreatedAt(Instant.now());

    return maintenance;

  }

  default MaintenanceResponse toMaintenanceResponse(Maintenance maintenance) {
    return new MaintenanceResponse(
            maintenance.getId(), maintenance.getItemId(), maintenance.getPerformedAt(),
            maintenance.getCertificateNumber(), maintenance.getIssuedBy(), maintenance.getReceiptUrl()
    );
  }


}
