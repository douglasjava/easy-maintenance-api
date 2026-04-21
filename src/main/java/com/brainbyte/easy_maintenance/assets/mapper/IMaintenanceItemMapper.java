package com.brainbyte.easy_maintenance.assets.mapper;

import com.brainbyte.easy_maintenance.assets.application.dto.CreateItemRequest;
import com.brainbyte.easy_maintenance.assets.application.dto.ItemResponse;
import com.brainbyte.easy_maintenance.assets.domain.MaintenanceItem;
import com.brainbyte.easy_maintenance.assets.domain.enums.ItemCategory;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

@Mapper(componentModel = "spring")
public interface IMaintenanceItemMapper {

  IMaintenanceItemMapper INSTANCE = Mappers.getMapper(IMaintenanceItemMapper.class);

  default MaintenanceItem toMaintenanceItem(String orgId, CreateItemRequest request) {

    return MaintenanceItem.builder()
            .organizationCode(orgId)
            .itemType(request.itemType())
            .itemCategory(request.itemCategory().isRegulatory() ? ItemCategory.REGULATORY : ItemCategory.OPERATIONAL)
            .normId(request.itemCategory().isRegulatory() ? request.normId() : null)
            .customPeriodUnit(request.itemCategory().isOperational() ? request.customPeriodUnit() : null)
            .customPeriodQty(request.itemCategory().isOperational() ? request.customPeriodQty() : null)
            .build();

  }

  // Used by single-item endpoints (create, update, findById) where canUpdate
  // is not relevant to the caller. Defaults to false / null so the record
  // compiles; callers that need the permission value use the overload below.
  default ItemResponse toItemResponse(MaintenanceItem maintenanceItem, String normName) {
    return toItemResponse(maintenanceItem, normName, false, null);
  }

  // Used by the list endpoint (findAll) where canUpdate is batch-resolved
  // by the service before mapping, avoiding N+1 queries.
  default ItemResponse toItemResponse(MaintenanceItem maintenanceItem, String normName,
                                      boolean canUpdate, String reason) {
    return new ItemResponse(
            maintenanceItem.getId(),
            maintenanceItem.getOrganizationCode(),
            maintenanceItem.getItemType(),
            maintenanceItem.getItemCategory(),
            maintenanceItem.getNormId(),
            maintenanceItem.getCustomPeriodUnit(),
            maintenanceItem.getCustomPeriodQty(),
            maintenanceItem.getLastPerformedAt(),
            maintenanceItem.getNextDueAt(),
            maintenanceItem.getStatus(),
            normName,
            canUpdate,
            reason
    );
  }

}
