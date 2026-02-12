package com.brainbyte.easy_maintenance.assets.mapper;

import com.brainbyte.easy_maintenance.assets.application.dto.MaintenanceAttachmentSimpleResponse;
import com.brainbyte.easy_maintenance.assets.application.dto.MaintenanceResponse;
import com.brainbyte.easy_maintenance.assets.application.dto.RegisterMaintenanceRequest;
import com.brainbyte.easy_maintenance.assets.domain.Maintenance;
import com.brainbyte.easy_maintenance.assets.domain.MaintenanceAttachment;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Mapper(componentModel = "spring")
public interface IMaintenanceMapper {

  IMaintenanceMapper INSTANCE = Mappers.getMapper(IMaintenanceMapper.class);

  default Maintenance toMaintenance(RegisterMaintenanceRequest req, Long itemId) {

    Maintenance maintenance = new Maintenance();
    maintenance.setItemId(itemId);
    maintenance.setPerformedAt(req.performedAt());
    maintenance.setType(req.type());
    maintenance.setPerformedBy(req.performedBy());
    maintenance.setCostCents(req.costCents());
    maintenance.setNextDueAt(req.nextDueAt());
    maintenance.setCreatedAt(Instant.now());

    return maintenance;

  }

  default MaintenanceResponse toMaintenanceResponse(Maintenance maintenance) {
    return new MaintenanceResponse(
            maintenance.getId(),
            maintenance.getItemId(),
            maintenance.getPerformedAt(),
            maintenance.getType(),
            maintenance.getPerformedBy(),
            maintenance.getCostCents(),
            maintenance.getNextDueAt(),
            List.of()
    );
  }

  @Mapping(target = "attachments", source = "attachments")
  MaintenanceResponse toMaintenanceResponse(Maintenance maintenance, List<MaintenanceAttachmentSimpleResponse> attachments);

  @Mapping(target = "attachmentType", source = "attachmentType")
  MaintenanceAttachmentSimpleResponse toAttachmentSimpleResponse(MaintenanceAttachment attachment);

  List<MaintenanceAttachmentSimpleResponse> toAttachmentSimpleResponseList(List<MaintenanceAttachment> attachments);


}
