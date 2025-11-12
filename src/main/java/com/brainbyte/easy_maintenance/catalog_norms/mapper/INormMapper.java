package com.brainbyte.easy_maintenance.catalog_norms.mapper;

import com.brainbyte.easy_maintenance.catalog_norms.application.dto.NormDTO;
import com.brainbyte.easy_maintenance.catalog_norms.domain.Norm;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

@Mapper(componentModel = "spring")
public interface INormMapper {

  INormMapper INSTANCE = Mappers.getMapper(INormMapper.class);

  NormDTO.NormResponse toResponse(Norm norm);

}
