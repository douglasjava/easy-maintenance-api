package com.brainbyte.easy_maintenance.assets.mapper;

import com.brainbyte.easy_maintenance.assets.application.dto.ItemTypesResponse;
import com.brainbyte.easy_maintenance.assets.domain.ItemTypes;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

@Mapper(componentModel = "spring")
public interface IItemTypesMapper {

    IItemTypesMapper INSTANCE = Mappers.getMapper(IItemTypesMapper.class);

    ItemTypesResponse toResponse(ItemTypes itemTypes);

}
