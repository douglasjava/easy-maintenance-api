package com.brainbyte.easy_maintenance.supplier.application.dto;

public record NearbySuppliersRequest(
        String serviceKey,     // ex: "EXTINTOR", "AR_COND", "SPDA"
        double lat,
        double lng,
        Integer radiusKm,      // opcional (default 20)
        Integer limit          // opcional (default max-results)
) {}
