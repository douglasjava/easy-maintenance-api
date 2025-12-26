package com.brainbyte.easy_maintenance.supplier.application.dto;

public record SupplierDTO(
        String placeId,
        String name,
        String address,
        Double rating,
        Integer userRatingsTotal,
        String phone,      // pode vir nulo se details desabilitado/indisponível
        String website,    // pode vir nulo
        String mapsUrl,    // url do Google Maps
        double lat,
        double lng
) {}
