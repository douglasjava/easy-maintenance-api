package com.brainbyte.easy_maintenance.infrastructure.saas;

import com.brainbyte.easy_maintenance.infrastructure.saas.application.dto.AsaasDTO;
import com.brainbyte.easy_maintenance.payment.application.dto.CustomerDTO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

@Mapper(componentModel = "spring")
public interface IAsaasMapper {

    IAsaasMapper INSTANCE = Mappers.getMapper(IAsaasMapper.class);

    default AsaasDTO.CreateCustomerRequest toCreateCustomerRequest(CustomerDTO customerDTO) {
        return new AsaasDTO.CreateCustomerRequest(
                customerDTO.getName(),
                customerDTO.getDoc(),
                customerDTO.getEmail(),
                customerDTO.getPhone(),
                customerDTO.getPhone(),
                customerDTO.getZipCode(),
                customerDTO.getStreet(),
                customerDTO.getNumber(),
                customerDTO.getComplement(),
                customerDTO.getNeighborhood(),
                customerDTO.getCity(),
                customerDTO.getState(),
                customerDTO.getCountry()
        );
    }


}
