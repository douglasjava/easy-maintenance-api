package com.brainbyte.easy_maintenance.billing.mapper;

import com.brainbyte.easy_maintenance.billing.application.dto.InvoiceDTO;
import com.brainbyte.easy_maintenance.billing.domain.Invoice;
import com.brainbyte.easy_maintenance.billing.domain.InvoiceItem;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

@Mapper(componentModel = "spring")
public interface InvoiceMapper {

    InvoiceMapper INSTANCE = Mappers.getMapper(InvoiceMapper.class);

    @Mapping(target = "payerUserId", source = "payer.id")
    InvoiceDTO.InvoiceResponse toInvoiceResponse(Invoice invoice);

}

