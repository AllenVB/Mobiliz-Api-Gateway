package com.mobiliz.ysbridge.gateway;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * A legacy API that stops answering.
 *
 * <p>Its own test class because it needs a read timeout short enough to trigger, which would make
 * every other test race it.
 *
 * <p>The distinction matters on call: a timeout means the request may well have been carried out
 * upstream, so retrying it blindly could repeat a write. "Unreachable" means it was not. Collapsing
 * both into 502 would hide that.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "ys-bridge.legacy.read-timeout=300ms")
class BridgeTimeoutTest {

    private static final WireMockServer LEGACY =
            new WireMockServer(WireMockConfiguration.options().dynamicPort());

    static {
        LEGACY.start();
    }

    @DynamicPropertySource
    static void pointTheBridgeAtWireMock(DynamicPropertyRegistry registry) {
        registry.add("ys-bridge.legacy.base-url", () -> "http://localhost:" + LEGACY.port());
    }

    @AfterAll
    static void stopLegacy() {
        LEGACY.stop();
    }

    @Autowired private TestRestTemplate http;

    @Test
    void shouldAnswer504WhenTheLegacyApiStopsResponding() {
        LEGACY.stubFor(
                get(urlPathEqualTo("/yetkiliservis/servistalepsatiri/abc"))
                        .willReturn(
                                aResponse()
                                        .withStatus(201)
                                        .withHeader("Content-Type", "application/json")
                                        .withFixedDelay(3000)
                                        .withBody("{\"success\": true}")));

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth("TEST-TOKEN");

        ResponseEntity<JsonNode> response =
                http.exchange(
                        "/authorized-service/service-request-line/abc",
                        HttpMethod.GET,
                        new HttpEntity<>(null, headers),
                        JsonNode.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
        assertThat(response.getBody().get("code").asText()).isEqualTo("UPSTREAM_TIMEOUT");
    }
}
