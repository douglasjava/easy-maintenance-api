package com.brainbyte.easy_maintenance.supplier.application.service;

import com.brainbyte.easy_maintenance.kernel.tenant.TenantContext;
import com.brainbyte.easy_maintenance.supplier.application.dto.GeoPoint;
import com.brainbyte.easy_maintenance.supplier.application.dto.NearbySuppliersRequest;
import com.brainbyte.easy_maintenance.supplier.application.dto.NearbySuppliersResponse;
import com.brainbyte.easy_maintenance.supplier.application.dto.SupplierDTO;
import com.brainbyte.easy_maintenance.supplier.application.properties.GooglePlacesProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

@Service
@RequiredArgsConstructor
public class SupplierSearchService {

  private final WebClient places;
  private final GooglePlacesProperties props;

  @Cacheable(value = "suppliersNearby", key = "#root.target.computeNearbyCacheKey(#req)")
  public NearbySuppliersResponse searchNearby(NearbySuppliersRequest req) {
    int radiusKm = (req.radiusKm() == null ? props.getDefaultRadiusM() / 1000 : req.radiusKm());
    int radiusM = radiusKm * 1000;
    int limit = (req.limit() == null ? props.getMaxResults() : Math.min(req.limit(), props.getMaxResults()));

    String keyword = mapServiceKeyToKeyword(req.serviceKey());

    // 1) Nearby Search (Legacy)
    NearbySearchLegacyResponse nearby = places.get()
            .uri(uri -> uri
                    .path("/nearbysearch/json")
                    .queryParam("location", req.lat() + "," + req.lng())
                    .queryParam("radius", radiusM)
                    .queryParam("keyword", keyword)
                    .queryParam("language", "pt-BR")
                    .queryParam("key", props.getApiKey())
                    .build())
            .retrieve()
            .bodyToMono(NearbySearchLegacyResponse.class)
            .block();

    GeoPoint geoPoint = new GeoPoint(req.lat(), req.lng());
    if (nearby == null || nearby.results == null) {
      return new NearbySuppliersResponse(req.serviceKey(), radiusKm, geoPoint, java.util.List.of());
    }

    var baseList = nearby.results.stream()
            .limit(limit)
            .map(r -> new SupplierDTO(
                    r.place_id,
                    r.name,
                    r.vicinity != null ? r.vicinity : r.formatted_address,
                    r.rating,
                    r.user_ratings_total,
                    null,
                    null,
                    "https://www.google.com/maps/search/?api=1&query=Google&query_place_id=" + r.place_id,
                    r.geometry.location.lat,
                    r.geometry.location.lng
            ))
            .toList();

    // 2) Opcional: enriquecer via Place Details (telefone/website)
    if (!props.isDetailsEnabled()) {
      return new NearbySuppliersResponse(req.serviceKey(), radiusKm, geoPoint, baseList);
    }

    var enriched = baseList.stream()
            .map(this::enrichWithDetails)
            .toList();

    return new NearbySuppliersResponse(req.serviceKey(), radiusKm, geoPoint, enriched);
  }

  // Compose cache key: orgId + rounded lat/lng (2 decimals) + effective radiusKm + limit
  public String computeNearbyCacheKey(NearbySuppliersRequest req) {
    String org = TenantContext.get().orElse("NO_ORG");
    int radiusKm = (req.radiusKm() == null ? props.getDefaultRadiusM() / 1000 : req.radiusKm());
    int limit = (req.limit() == null ? props.getMaxResults() : Math.min(req.limit(), props.getMaxResults()));
    double rlat = Math.round(req.lat() * 100.0) / 100.0;
    double rlng = Math.round(req.lng() * 100.0) / 100.0;
    return org + ":" + rlat + ":" + rlng + ":" + radiusKm + ":" + limit;
  }

  private SupplierDTO enrichWithDetails(SupplierDTO s) {
    PlaceDetailsLegacyResponse details = places.get()
            .uri(uri -> uri
                    .path("/details/json")
                    .queryParam("place_id", s.placeId())
                    .queryParam("fields", "name,formatted_address,formatted_phone_number,website,url,rating,user_ratings_total")
                    .queryParam("language", "pt-BR")
                    .queryParam("key", props.getApiKey())
                    .build())
            .retrieve()
            .bodyToMono(PlaceDetailsLegacyResponse.class)
            .block();

    if (details == null || details.result == null) return s;

    var r = details.result;
    return new SupplierDTO(
            s.placeId(),
            r.name != null ? r.name : s.name(),
            r.formatted_address != null ? r.formatted_address : s.address(),
            r.rating != null ? r.rating : s.rating(),
            r.user_ratings_total != null ? r.user_ratings_total : s.userRatingsTotal(),
            r.formatted_phone_number,
            r.website,
            r.url != null ? r.url : s.mapsUrl(),
            s.lat(),
            s.lng()
    );
  }

  private String mapServiceKeyToKeyword(String serviceKey) {
    if (serviceKey == null) return "manutenção predial";
    return switch (serviceKey.toUpperCase()) {
      case "EXTINTOR" -> "manutenção de extintores recarga inspeção";
      case "SPDA" -> "inspeção SPDA para-raios NBR 5419";
      case "CAIXA_DAGUA" -> "limpeza de caixa d'água";
      case "ILUMINACAO_EMERGENCIA" -> "manutenção iluminação de emergência";
      case "HIDRANTE" -> "manutenção de hidrantes e bomba de incêndio";
      case "AR_COND" -> "manutenção ar condicionado limpeza PMOC";
      default -> "manutenção " + serviceKey;
    };
  }

  // ====== modelos internos p/ parse do JSON do Google (mínimos) ======

  static class NearbySearchLegacyResponse {
    public java.util.List<NearbyResult> results;
    public String status;
  }
  static class NearbyResult {
    public String place_id;
    public String name;
    public String vicinity;
    public String formatted_address;
    public Double rating;
    public Integer user_ratings_total;
    public Geometry geometry;
  }
  static class Geometry {
    public LatLng location;
  }
  static class LatLng {
    public double lat;
    public double lng;
  }

  static class PlaceDetailsLegacyResponse {
    public PlaceDetailsResult result;
    public String status;
  }
  static class PlaceDetailsResult {
    public String name;
    public String formatted_address;
    public String formatted_phone_number;
    public String website;
    public String url;
    public Double rating;
    public Integer user_ratings_total;
  }
}
