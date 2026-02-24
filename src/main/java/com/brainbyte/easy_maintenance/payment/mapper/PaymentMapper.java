package com.brainbyte.easy_maintenance.payment.mapper;

import com.brainbyte.easy_maintenance.payment.application.dto.PaymentResponse;
import com.brainbyte.easy_maintenance.payment.domain.Payment;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

@Mapper(componentModel = "spring")
public interface PaymentMapper {

    PaymentMapper INSTANCE = Mappers.getMapper(PaymentMapper.class);

    @Mapping(target = "invoiceId", source = "invoice.id")
    @Mapping(target = "payerUserId", source = "payer.id")
    PaymentResponse toResponse(Payment payment);
}
