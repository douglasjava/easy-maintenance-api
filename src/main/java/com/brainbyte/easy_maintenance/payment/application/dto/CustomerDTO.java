package com.brainbyte.easy_maintenance.payment.application.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CustomerDTO {
    
    private String name;
    private String email;
    private String doc;
    private String street;
    private String number;
    private String complement;
    private String neighborhood;
    private String city;
    private String state;
    private String zipCode;
    private String country;
    private String phone;
    
}
