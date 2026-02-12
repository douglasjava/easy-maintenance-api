package com.brainbyte.easy_maintenance.assets.mapper;

import com.brainbyte.easy_maintenance.assets.application.dto.MaintenanceAttachmentResponse;
import com.brainbyte.easy_maintenance.assets.domain.MaintenanceAttachment;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

@Mapper(componentModel = "spring")
public interface IMaintenanceAttachmentMapper {

    IMaintenanceAttachmentMapper INSTANCE = Mappers.getMapper(IMaintenanceAttachmentMapper.class);

    MaintenanceAttachmentResponse toResponse(MaintenanceAttachment attachment);
}
