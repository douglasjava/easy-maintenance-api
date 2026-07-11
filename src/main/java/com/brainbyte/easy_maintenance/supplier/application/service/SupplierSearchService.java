package com.brainbyte.easy_maintenance.supplier.application.service;

import com.brainbyte.easy_maintenance.kernel.tenant.TenantContext;
import com.brainbyte.easy_maintenance.supplier.application.dto.GeoPoint;
import com.brainbyte.easy_maintenance.supplier.application.dto.NearbySuppliersRequest;
import com.brainbyte.easy_maintenance.supplier.application.dto.NearbySuppliersResponse;
import com.brainbyte.easy_maintenance.supplier.application.dto.SupplierDTO;
import com.brainbyte.easy_maintenance.supplier.application.properties.GooglePlacesProperties;
import io.sentry.Sentry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;

@Slf4j
@Service
public class SupplierSearchService {

    private static final String STATUS_OK = "OK";
    private static final String STATUS_ZERO_RESULTS = "ZERO_RESULTS";
    private static final String STATUS_PROVIDER_UNAVAILABLE = "PROVIDER_UNAVAILABLE";

    private final WebClient places;
    private final GooglePlacesProperties props;

    public SupplierSearchService(@Qualifier("googlePlacesWebClient") WebClient places, GooglePlacesProperties props) {
        this.places = places;
        this.props = props;
    }

    @Cacheable(value = "suppliersNearby", key = "#root.target.computeNearbyCacheKey(#req)")
    public NearbySuppliersResponse searchNearby(NearbySuppliersRequest req) {

        int radiusKm = resolveRadiusKm(req);
        int limit = resolveLimit(req);
        GeoPoint center = new GeoPoint(req.lat(), req.lng());
        String keyword = mapServiceKeyToKeyword(req.serviceKey());

        log.info("Busca de fornecedores próximos iniciada: serviceKey={} radiusKm={} limit={}",
                req.serviceKey(), radiusKm, limit);

        NearbySearchLegacyResponse nearby = fetchNearbySearch(req, radiusKm, keyword);

        if (nearby == null) {
            return emptyResponse(req, radiusKm, center, STATUS_PROVIDER_UNAVAILABLE,
                    "Falha de comunicação com o provedor de busca.");
        }

        if (nearby.results == null) {
            return emptyResponse(req, radiusKm, center, nearby.status, nearby.error_message);
        }

        if (!isSuccessStatus(nearby.status)) {
            logProviderError(req.serviceKey(), nearby.status, nearby.error_message);
            return emptyResponse(req, radiusKm, center, nearby.status, nearby.error_message);
        }

        List<SupplierDTO> suppliers = buildSuppliers(nearby, limit);
        if (props.isDetailsEnabled()) {
            suppliers = enrichAll(suppliers);
        }

        log.info("Busca de fornecedores próximos concluída: serviceKey={} status={} encontrados={}",
                req.serviceKey(), nearby.status, suppliers.size());

        return new NearbySuppliersResponse(
                req.serviceKey(),
                radiusKm,
                center,
                suppliers,
                nearby.status,
                nearby.error_message
        );

    }

    public String computeNearbyCacheKey(NearbySuppliersRequest req) {
        String org = TenantContext.get().orElse("NO_ORG");
        int radiusKm = resolveRadiusKm(req);
        int limit = resolveLimit(req);
        double rlat = Math.round(req.lat() * 100.0) / 100.0;
        double rlng = Math.round(req.lng() * 100.0) / 100.0;
        return org + ":" + rlat + ":" + rlng + ":" + radiusKm + ":" + limit;
    }

    private int resolveRadiusKm(NearbySuppliersRequest req) {
        return req.radiusKm() == null ? props.getDefaultRadiusM() / 1000 : req.radiusKm();
    }

    private int resolveLimit(NearbySuppliersRequest req) {
        return req.limit() == null ? props.getMaxResults() : Math.min(req.limit(), props.getMaxResults());
    }

    private boolean isSuccessStatus(String status) {
        return STATUS_OK.equals(status) || STATUS_ZERO_RESULTS.equals(status);
    }

    private NearbySearchLegacyResponse fetchNearbySearch(NearbySuppliersRequest req, int radiusKm, String keyword) {
        try {
            NearbySearchLegacyResponse response = places.get()
                    .uri(uri -> uri
                            .path("/nearbysearch/json")
                            .queryParam("location", req.lat() + "," + req.lng())
                            .queryParam("radius", radiusKm * 1000)
                            .queryParam("keyword", keyword)
                            .queryParam("language", "pt-BR")
                            .queryParam("key", props.getApiKey())
                            .build())
                    .retrieve()
                    .bodyToMono(NearbySearchLegacyResponse.class)
                    .block();

            log.debug("Google Places nearbysearch respondeu: serviceKey={} status={} totalResultados={}",
                    req.serviceKey(),
                    response != null ? response.status : null,
                    response != null && response.results != null ? response.results.size() : 0);

            return response;
        } catch (Exception ex) {
            log.error("Falha ao chamar Google Places nearbysearch: serviceKey={}", req.serviceKey(), ex);
            Sentry.captureException(ex);
            return null;
        }
    }

    private void logProviderError(String serviceKey, String status, String errorMessage) {
        String reason = errorMessage != null ? errorMessage : "(sem error_message)";
        log.error("Google Places nearbysearch retornou status inesperado: status={} errorMessage={} serviceKey={}",
                status, reason, serviceKey);
        Sentry.captureMessage("Google Places nearbysearch " + status + ": " + reason);
    }

    private NearbySuppliersResponse emptyResponse(NearbySuppliersRequest req, int radiusKm, GeoPoint center,
                                                  String status, String errorMessage) {
        return new NearbySuppliersResponse(req.serviceKey(), radiusKm, center, List.of(), status, errorMessage);
    }

    private List<SupplierDTO> buildSuppliers(NearbySearchLegacyResponse nearby, int limit) {
        return nearby.results.stream()
                .limit(limit)
                .map(this::toSupplierDTO)
                .toList();
    }

    private SupplierDTO toSupplierDTO(NearbyResult r) {
        return new SupplierDTO(
                r.place_id,
                r.name,
                r.vicinity != null ? r.vicinity : r.formatted_address,
                r.rating,
                r.user_ratings_total,
                null,
                null,
                mapsUrl(r.place_id),
                r.geometry.location.lat,
                r.geometry.location.lng
        );
    }

    private String mapsUrl(String placeId) {
        return "https://www.google.com/maps/search/?api=1&query=Google&query_place_id=" + placeId;
    }

    private List<SupplierDTO> enrichAll(List<SupplierDTO> suppliers) {
        return suppliers.stream().map(this::enrichWithDetails).toList();
    }

    private SupplierDTO enrichWithDetails(SupplierDTO s) {
        PlaceDetailsLegacyResponse details = fetchPlaceDetails(s.placeId());
        if (details == null || details.result == null) {
            return s;
        }
        return mergeDetails(s, details.result);
    }

    private PlaceDetailsLegacyResponse fetchPlaceDetails(String placeId) {
        try {
            PlaceDetailsLegacyResponse details = places.get()
                    .uri(uri -> uri
                            .path("/details/json")
                            .queryParam("place_id", placeId)
                            .queryParam("fields", "name,formatted_address,formatted_phone_number,website,url,rating,user_ratings_total")
                            .queryParam("language", "pt-BR")
                            .queryParam("key", props.getApiKey())
                            .build())
                    .retrieve()
                    .bodyToMono(PlaceDetailsLegacyResponse.class)
                    .block();

            if (details != null && !STATUS_OK.equals(details.status)) {
                log.warn("Google Place Details retornou status inesperado: status={} errorMessage={} placeId={}",
                        details.status, details.error_message, placeId);
            }
            return details;
        } catch (Exception ex) {
            log.warn("Falha ao chamar Google Place Details: placeId={} erro={}", placeId, ex.getMessage());
            return null;
        }
    }

    private SupplierDTO mergeDetails(SupplierDTO s, PlaceDetailsResult r) {
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

    static class NearbySearchLegacyResponse {
        public List<NearbyResult> results;
        public String status;
        public String error_message;
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
        public String error_message;
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
