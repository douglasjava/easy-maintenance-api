package com.brainbyte.easy_maintenance.payment.mapper;

import com.brainbyte.easy_maintenance.payment.application.dto.PaymentMethodDTO;
import com.brainbyte.easy_maintenance.payment.domain.PaymentMethod;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

@Mapper(componentModel = "spring")
public interface PaymentMethodMapper {

    PaymentMethodMapper INSTANCE = Mappers.getMapper(PaymentMethodMapper.class);

    @Mapping(target = "userId", source = "user.id")
    PaymentMethodDTO.PaymentMethodResponse toResponse(PaymentMethod entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "user", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    PaymentMethod toEntity(PaymentMethodDTO.CreatePaymentMethodRequest request);
}
