package com.brainbyte.easy_maintenance.supplier.application.dto;

import java.util.List;

public record NearbySuppliersResponse(
        String serviceKey,
        int radiusKm,
        GeoPoint center,
        List<SupplierDTO> suppliers
) {}
