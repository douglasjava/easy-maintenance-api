package com.brainbyte.easy_maintenance.supplier.application.service;

import com.brainbyte.easy_maintenance.supplier.application.dto.NearbySuppliersRequest;
import com.brainbyte.easy_maintenance.supplier.application.dto.NearbySuppliersResponse;
import com.brainbyte.easy_maintenance.supplier.application.properties.GooglePlacesProperties;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class SupplierSearchServiceTest {

    private HttpServer server;
    private SupplierSearchService service;
    private String lastRequestPath;

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.start();

        GooglePlacesProperties props = new GooglePlacesProperties(
                "fake-key",
                "http://localhost:" + server.getAddress().getPort(),
                20000,
                5,
                false
        );
        WebClient webClient = WebClient.builder().baseUrl(props.getBaseUrl()).build();
        service = new SupplierSearchService(webClient, props);
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    private void stubNearbySearch(String body) {
        server.createContext("/nearbysearch/json", exchange -> {
            lastRequestPath = exchange.getRequestURI().toString();
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
    }

    @Test
    void searchNearby_whenGoogleReturnsRequestDenied_returnsEmptyListInsteadOfThrowing() {
        stubNearbySearch("""
                {
                  "status": "REQUEST_DENIED",
                  "error_message": "You must enable Billing on the Google Cloud Project",
                  "results": []
                }
                """);

        NearbySuppliersRequest req = new NearbySuppliersRequest("EXTINTOR", -19.9245, -43.9352, 20, 5);

        NearbySuppliersResponse response = service.searchNearby(req);

        assertThat(response.suppliers()).isEmpty();
        assertThat(response.serviceKey()).isEqualTo("EXTINTOR");
        assertThat(response.radiusKm()).isEqualTo(20);
        assertThat(response.status()).isEqualTo("REQUEST_DENIED");
        assertThat(response.errorMessage()).isEqualTo("You must enable Billing on the Google Cloud Project");
    }

    @Test
    void searchNearby_whenGoogleReturnsZeroResults_returnsEmptyList() {
        stubNearbySearch("""
                {
                  "status": "ZERO_RESULTS",
                  "results": []
                }
                """);

        NearbySuppliersRequest req = new NearbySuppliersRequest("EXTINTOR", -19.9245, -43.9352, 20, 5);

        NearbySuppliersResponse response = service.searchNearby(req);

        assertThat(response.suppliers()).isEmpty();
        assertThat(response.status()).isEqualTo("ZERO_RESULTS");
        assertThat(response.errorMessage()).isNull();
    }

    @Test
    void searchNearby_whenGoogleReturnsOkWithResults_mapsSuppliers() {
        stubNearbySearch("""
                {
                  "status": "OK",
                  "results": [
                    {
                      "place_id": "abc123",
                      "name": "Extintores Minas Ltda",
                      "vicinity": "Rua Tal, 123",
                      "rating": 4.5,
                      "user_ratings_total": 10,
                      "geometry": { "location": { "lat": -19.92, "lng": -43.93 } }
                    }
                  ]
                }
                """);

        NearbySuppliersRequest req = new NearbySuppliersRequest("EXTINTOR", -19.9245, -43.9352, 20, 5);

        NearbySuppliersResponse response = service.searchNearby(req);

        assertThat(response.suppliers()).hasSize(1);
        assertThat(response.suppliers().get(0).placeId()).isEqualTo("abc123");
        assertThat(response.suppliers().get(0).name()).isEqualTo("Extintores Minas Ltda");
        assertThat(response.status()).isEqualTo("OK");
    }

    @Test
    void searchNearby_whenTransportFails_returnsEmptyListInsteadOfThrowing() {
        server.stop(0);

        NearbySuppliersRequest req = new NearbySuppliersRequest("EXTINTOR", -19.9245, -43.9352, 20, 5);

        NearbySuppliersResponse response = service.searchNearby(req);

        assertThat(response.suppliers()).isEmpty();
        assertThat(response.status()).isEqualTo("PROVIDER_UNAVAILABLE");
        assertThat(response.errorMessage()).isNotNull();
    }
}
