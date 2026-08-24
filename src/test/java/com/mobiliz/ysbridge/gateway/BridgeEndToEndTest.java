package com.mobiliz.ysbridge.gateway;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import java.net.URI;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Drives the whole bridge over real HTTP, with WireMock standing in for the legacy API.
 *
 * <p>These are the tests that prove the thing actually works: an English request goes in, the
 * legacy API is shown to receive Turkish, and an English response with a truthful HTTP status comes
 * back out.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "ys-bridge.pagination.default-size=2",
            "ys-bridge.pagination.max-size=10",
            // Small enough that the rejected remainder still fits in the container's swallow
            // budget, so the caller receives the 413 instead of a dropped connection.
            "ys-bridge.limits.max-request-body=4KB"
        })
class BridgeEndToEndTest {

    private static final String TOKEN = "TEST-TOKEN-0000";

    private static final WireMockServer LEGACY =
            new WireMockServer(WireMockConfiguration.options().dynamicPort());

    static {
        // Started here rather than in @BeforeAll: @DynamicPropertySource runs first and
        // needs the port already assigned.
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

    @LocalServerPort private int port;

    @BeforeEach
    void resetLegacy() {
        LEGACY.resetAll();
    }

    // ------------------------------------------------------------------ helpers

    private static String envelope(String result) {
        return """
                {"success": true, "type": "SUCCESS", "message": null, "result": %s}"""
                .formatted(result);
    }

    private static String failure(String message) {
        return """
                {"success": false, "type": "ERROR", "message": "%s", "result": null}"""
                .formatted(message);
    }

    private static HttpHeaders authenticated() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(TOKEN);
        return headers;
    }

    private ResponseEntity<JsonNode> send(
            HttpMethod method, String path, String body, HttpHeaders headers) {
        HttpHeaders effective = headers == null ? new HttpHeaders() : headers;
        if (body != null && effective.getContentType() == null) {
            effective.setContentType(MediaType.APPLICATION_JSON);
        }
        return http.exchange(path, method, new HttpEntity<>(body, effective), JsonNode.class);
    }

    /**
     * The payload inside the envelope — what a caller is actually after.
     *
     * <p>Going through here rather than {@code getBody()} means every payload assertion in this
     * suite also asserts that the envelope is present and reports success.
     */
    private static JsonNode result(ResponseEntity<JsonNode> response) {
        JsonNode body = response.getBody();
        assertThat(body).as("every response carries the envelope").isNotNull();
        assertThat(body.get("success").asBoolean()).as("envelope reports success").isTrue();
        return body.get(ExposedEnvelope.RESULT);
    }

    /** A GET carrying a valid bearer token. */
    private ResponseEntity<JsonNode> callGet(String path) {
        return send(HttpMethod.GET, path, null, authenticated());
    }

    private static void stubGet(String legacyPath, String body) {
        LEGACY.stubFor(
                get(urlPathEqualTo(legacyPath))
                        .willReturn(
                                aResponse()
                                        .withStatus(201) // the legacy API says 201 for everything
                                        .withHeader("Content-Type", "application/json")
                                        .withBody(body)));
    }

    // ========================================================================= login

    @Nested
    @DisplayName("POST /authorized-service/login")
    class Login {

        private static final String LEGACY_LOGIN_RESULT =
                """
                { "token": "TEST-TOKEN", "adSoyad": "Saha Destek 1", "rol": "1",
                  "rolAciklama": "test", "qr": false,
                  "androidversion": "2.0.3", "iosversion": "1.0(47)",
                  "satisandroidversion": "1.0.3", "satisiosversion": "1.0 (29)",
                  "YSKVKK": true, "YSETK": true, "SatisKVKK": true, "SatisETK": true }
                """;

        @Test
        void shouldSendTurkishFieldsToTheLegacyApiAndReturnEnglishOnes() {
            LEGACY.stubFor(
                    post(urlPathEqualTo("/yetkiliservis/giris"))
                            .willReturn(
                                    aResponse()
                                            .withStatus(201)
                                            .withHeader("Content-Type", "application/json")
                                            .withBody(envelope(LEGACY_LOGIN_RESULT))));

            ResponseEntity<JsonNode> response =
                    send(
                            HttpMethod.POST,
                            "/authorized-service/login",
                            """
                            {"username": "test-user", "password": "test-password",
                             "osVersion": "IOS", "deviceToken": null}""",
                            null);

            // The legacy API must have been given its own vocabulary.
            LEGACY.verify(
                    postRequestedFor(urlPathEqualTo("/yetkiliservis/giris"))
                            .withRequestBody(
                                    equalToJson(
                                            """
                                            {"kullaniciAdi": "test-user", "sifre": "test-password",
                                             "osversiyon": "IOS", "deviceToken": null}""")));

            // The caller must get a real status, not the legacy 201, and English names.
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode body = result(response);
            assertThat(body.get("fullname").asText()).isEqualTo("Saha Destek 1");
            assertThat(body.get("role").asText()).isEqualTo("1");
            assertThat(body.get("roleDescription").asText()).isEqualTo("test");
            assertThat(body.get("androidVersion").asText()).isEqualTo("2.0.3");
            assertThat(body.get("salesEtk").asBoolean()).isTrue();
            assertThat(body.has("adSoyad")).as("no legacy name may survive").isFalse();
        }

        @Test
        void shouldNotRequireATokenToLogIn() {
            LEGACY.stubFor(
                    post(urlPathEqualTo("/yetkiliservis/giris"))
                            .willReturn(
                                    aResponse()
                                            .withStatus(201)
                                            .withHeader("Content-Type", "application/json")
                                            .withBody(envelope(LEGACY_LOGIN_RESULT))));

            ResponseEntity<JsonNode> response =
                    send(
                            HttpMethod.POST,
                            "/authorized-service/login",
                            """
                            {"username": "u", "password": "p", "osVersion": "IOS"}""",
                            null);

            assertThat(response.getStatusCode())
                    .as("login is how a caller obtains a token, so it cannot demand one")
                    .isEqualTo(HttpStatus.OK);
        }

        @Test
        void shouldRejectAFieldThatIsNotPartOfTheExposedContract() {
            ResponseEntity<JsonNode> response =
                    send(
                            HttpMethod.POST,
                            "/authorized-service/login",
                            """
                            {"username": "u", "password": "p", "sifre": "oops"}""",
                            null);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody().get("code").asText()).isEqualTo("UNRECOGNISED_FIELD");
            assertThat(response.getBody().get("field").asText()).isEqualTo("sifre");
            LEGACY.verify(0, postRequestedFor(urlPathEqualTo("/yetkiliservis/giris")));
        }
    }

    // =============================================================== authentication

    @Nested
    @DisplayName("authentication")
    class Authentication {

        @Test
        void shouldRejectAnUnauthenticatedRequestWithoutTroublingTheLegacyApi() {
            ResponseEntity<JsonNode> response =
                    send(
                            HttpMethod.GET,
                            "/authorized-service/service-request-line/abc",
                            null,
                            null);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(response.getBody().get("code").asText()).isEqualTo("MISSING_TOKEN");
            assertThat(response.getHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE))
                    .isEqualTo("Bearer");
            LEGACY.verify(
                    0, getRequestedFor(urlPathEqualTo("/yetkiliservis/servistalepsatiri/abc")));
        }

        @Test
        void shouldTranslateTheBearerTokenIntoTheHeaderTheLegacyApiReads() {
            stubGet(
                    "/yetkiliservis/servistalepsatiri/abc",
                    envelope(
                            """
                    {"plaka": "00XXX000"}"""));

            ResponseEntity<JsonNode> response =
                    callGet("/authorized-service/service-request-line/abc");

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(result(response).get("plate").asText()).isEqualTo("00XXX000");

            // Exposed contract: Authorization: Bearer. Legacy contract: a bare `token` header.
            LEGACY.verify(
                    getRequestedFor(urlPathEqualTo("/yetkiliservis/servistalepsatiri/abc"))
                            .withHeader("token", equalTo(TOKEN)));
        }

        @Test
        void shouldIgnoreAnAuthorizationHeaderThatIsNotBearer() {
            HttpHeaders headers = new HttpHeaders();
            headers.set(HttpHeaders.AUTHORIZATION, "Basic dXNlcjpwYXNz");

            ResponseEntity<JsonNode> response =
                    send(
                            HttpMethod.GET,
                            "/authorized-service/service-request-line/abc",
                            null,
                            headers);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        void shouldNotForwardHeadersThatAreNotOnTheAllowList() {
            stubGet("/yetkiliservis/servistalepsatiri/abc", envelope("{}"));

            HttpHeaders headers = authenticated();
            headers.set("X-Internal-Secret", "must-not-leak");
            send(HttpMethod.GET, "/authorized-service/service-request-line/abc", null, headers);

            assertThat(
                            LEGACY.findAll(
                                            getRequestedFor(
                                                    urlPathEqualTo(
                                                            "/yetkiliservis/servistalepsatiri/abc")))
                                    .getFirst()
                                    .getHeaders()
                                    .getHeader("X-Internal-Secret")
                                    .isPresent())
                    .as("only allow-listed headers may leave this service")
                    .isFalse();
        }
    }

    // ================================================== query parameter to header

    @Nested
    @DisplayName("query parameters the legacy API wants as headers")
    class QueryToHeader {

        @Test
        void shouldMoveTheSearchTermFromTheQueryStringIntoTheLegacyHeader() {
            stubGet(
                    "/yetkiliservis/SaseSorgulama",
                    envelope(
                            """
                            [{"id": "1", "SaseNo": "VIN000"}]"""));

            ResponseEntity<JsonNode> response =
                    callGet("/authorized-service/chassis-inquiry?serialNumber=211");

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(result(response).get("items").get(0).get("chassisNo").asText())
                    .isEqualTo("VIN000");

            LEGACY.verify(
                    getRequestedFor(urlPathEqualTo("/yetkiliservis/SaseSorgulama"))
                            .withHeader("search", equalTo("211")));
        }

        @Test
        void shouldRejectAQueryParameterThatCannotTravelInAHeader() {
            // A CR or LF would end the header and let what follows be read as another one.
            // The HTTP client refuses the value anyway, so nothing is smuggled upstream — but
            // it refuses by throwing, and a caller's mistake must not read as a 500.
            // Built as a URI so the percent-encoding survives to the wire: a String path
            // would be encoded again, and the control characters would never arrive.
            ResponseEntity<JsonNode> response =
                    http.exchange(
                            URI.create(
                                    "http://localhost:%d/authorized-service/chassis-inquiry"
                                                    .formatted(port)
                                            + "?serialNumber=211%0d%0aX-Evil:+1"),
                            HttpMethod.GET,
                            new HttpEntity<>(null, authenticated()),
                            JsonNode.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody().get("code").asText())
                    .isEqualTo("INVALID_QUERY_PARAMETER");
            assertThat(response.getBody().get("parameter").asText()).isEqualTo("serialNumber");
            LEGACY.verify(0, getRequestedFor(urlPathEqualTo("/yetkiliservis/SaseSorgulama")));
        }

        @Test
        void shouldRejectTheRequestWhenARequiredParameterIsMissing() {
            ResponseEntity<JsonNode> response = callGet("/authorized-service/chassis-inquiry");

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody().get("code").asText())
                    .isEqualTo("MISSING_QUERY_PARAMETER");
            assertThat(response.getBody().get("parameter").asText()).isEqualTo("serialNumber");
            LEGACY.verify(0, getRequestedFor(urlPathEqualTo("/yetkiliservis/SaseSorgulama")));
        }
    }

    // ================================================================== collections

    @Nested
    @DisplayName("collections")
    class Collections {

        /** Five lines, so the configured page size of two gives three pages. */
        private static final String FIVE_LINES =
                envelope(
                        """
                        [{"detayId": "A"}, {"detayId": "B"}, {"detayId": "C"},
                         {"detayId": "D"}, {"detayId": "E"}]""");

        @Test
        void shouldNeverReturnABareArray() {
            stubGet("/yetkiliservis/servistalepsatirlari/WO-1", FIVE_LINES);

            JsonNode body = result(callGet("/authorized-service/service-request-lines/WO-1"));

            assertThat(body.isArray())
                    .as("a bare array can never gain a field without breaking every caller")
                    .isFalse();
            assertThat(body.has("items")).isTrue();
            assertThat(body.has("page")).isTrue();
        }

        @Test
        void shouldReportWhereInTheCollectionThePageSits() {
            stubGet("/yetkiliservis/servistalepsatirlari/WO-1", FIVE_LINES);

            JsonNode page =
                    result(callGet("/authorized-service/service-request-lines/WO-1?page=1"));

            assertThat(page.get("items")).hasSize(2);
            assertThat(page.get("items").get(0).get("detailId").asText()).isEqualTo("C");
            assertThat(page.get("page").get("number").asInt()).isEqualTo(1);
            assertThat(page.get("page").get("size").asInt()).isEqualTo(2);
            assertThat(page.get("page").get("totalElements").asInt()).isEqualTo(5);
            assertThat(page.get("page").get("totalPages").asInt()).isEqualTo(3);
        }

        @Test
        void shouldReturnTheLastPartialPage() {
            stubGet("/yetkiliservis/servistalepsatirlari/WO-1", FIVE_LINES);

            JsonNode page =
                    result(callGet("/authorized-service/service-request-lines/WO-1?page=2"));

            assertThat(page.get("items")).hasSize(1);
            assertThat(page.get("items").get(0).get("detailId").asText()).isEqualTo("E");
        }

        @Test
        void shouldReturnAnEmptyPageBeyondTheEndRatherThanFailing() {
            stubGet("/yetkiliservis/servistalepsatirlari/WO-1", FIVE_LINES);

            JsonNode page =
                    result(callGet("/authorized-service/service-request-lines/WO-1?page=99"));

            assertThat(page.get("items")).isEmpty();
            assertThat(page.get("page").get("totalElements").asInt()).isEqualTo(5);
        }

        @Test
        void shouldStayEmptyWhenThePageNumberIsLargeEnoughToOverflow() {
            // page * size is a multiplication, and a caller chooses both. Large enough values
            // wrap to a negative offset, which reads before the start of the array; Jackson
            // answers null for those indices and they arrive as a page full of nulls.
            stubGet("/yetkiliservis/servistalepsatirlari/WO-1", FIVE_LINES);

            JsonNode page =
                    result(
                            callGet(
                                    "/authorized-service/service-request-lines/WO-1"
                                            + "?page=300000000&size=10"));

            assertThat(page.get("items"))
                    .as("a page past the end is empty, never a list of nulls")
                    .isEmpty();
            assertThat(page.get("page").get("totalElements").asInt()).isEqualTo(5);
        }

        @Test
        void shouldReportZeroPagesForAnEmptyCollection() {
            stubGet("/yetkiliservis/servistalepsatirlari/WO-1", envelope("[]"));

            JsonNode page = result(callGet("/authorized-service/service-request-lines/WO-1"));

            assertThat(page.get("items")).isEmpty();
            assertThat(page.get("page").get("totalPages").asInt())
                    .as("an empty collection has no pages, not one empty one")
                    .isZero();
        }

        @Test
        void shouldRejectAPageSizeBeyondTheConfiguredMaximum() {
            ResponseEntity<JsonNode> response =
                    callGet("/authorized-service/service-request-lines/WO-1?size=1000");

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody().get("code").asText()).isEqualTo("INVALID_PAGE_REQUEST");
            assertThat(response.getBody().get("parameter").asText()).isEqualTo("size");
        }

        @Test
        void shouldRejectAPagingParameterThatIsNotANumber() {
            ResponseEntity<JsonNode> response =
                    callGet("/authorized-service/service-request-lines/WO-1?page=first");

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody().get("parameter").asText()).isEqualTo("page");
        }
    }

    // ====================================================== envelope and statuses

    @Nested
    @DisplayName("envelope interpretation")
    class EnvelopeInterpretation {

        @Test
        void shouldAnswer200WithANullResultWhenTheLegacySuccessCarriesNoPayload() {
            LEGACY.stubFor(
                    post(urlPathEqualTo("/yetkiliservis/sifremiunuttum"))
                            .willReturn(
                                    aResponse()
                                            .withStatus(201)
                                            .withHeader("Content-Type", "application/json")
                                            .withBody(envelope("null"))));

            ResponseEntity<JsonNode> response =
                    send(
                            HttpMethod.POST,
                            "/authorized-service/forgot-password",
                            """
                            {"username": "test-user"}""",
                            null);

            // Not 204: a 204 may carry no body, and the envelope is unconditional.
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().get("success").asBoolean()).isTrue();
            assertThat(result(response).isNull())
                    .as("an operation with nothing to return says so inside the envelope")
                    .isTrue();
        }

        @Test
        void shouldTranslateAKnownLegacyErrorIntoItsCataloguedStatus() {
            stubGet("/yetkiliservis/servistalepsatiri/abc", failure("Token Bilgisi Eksik"));

            ResponseEntity<JsonNode> response =
                    callGet("/authorized-service/service-request-line/abc");

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            JsonNode problem = response.getBody();
            assertThat(problem.get("code").asText()).isEqualTo("MISSING_TOKEN");
            assertThat(problem.get("legacyMessage").asText()).isEqualTo("Token Bilgisi Eksik");
        }

        @Test
        void shouldTellAMissingTokenApartFromARejectedOne() {
            // "Eksik" means none was sent; "Hatalı" means one was sent and rejected. The
            // caller does different things with those, so they must not share a code.
            stubGet("/yetkiliservis/servistalepsatiri/abc", failure("Token Bilgisi Hatalı"));

            ResponseEntity<JsonNode> rejected =
                    callGet("/authorized-service/service-request-line/abc");

            assertThat(rejected.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(rejected.getBody().get("code").asText()).isEqualTo("INVALID_TOKEN");

            LEGACY.resetAll();
            stubGet("/yetkiliservis/servistalepsatiri/abc", failure("Token Bilgisi Eksik"));

            ResponseEntity<JsonNode> missing =
                    callGet("/authorized-service/service-request-line/abc");

            assertThat(missing.getBody().get("code").asText())
                    .as("same status, different cause, different code")
                    .isEqualTo("MISSING_TOKEN");
        }

        @Test
        void shouldNotRepeatItselfInTheProblemDocument() {
            stubGet("/yetkiliservis/servistalepsatiri/abc", failure("Token Bilgisi Eksik"));

            JsonNode problem = callGet("/authorized-service/service-request-line/abc").getBody();

            assertThat(problem.has("detail"))
                    .as("detail would only repeat title, so it is left out")
                    .isFalse();
            assertThat(problem.has("legacyType"))
                    .as("every legacy failure says ERROR, which the status already says")
                    .isFalse();
        }

        @Test
        void shouldKeepThePayloadThatArrivesAlongsideAFailure() {
            stubGet(
                    "/yetkiliservis/cihaztamamlandi/abc",
                    """
                    {"success": false, "type": "ERROR",
                     "message": "Cihaz GPS Time Dolu Değil. Lütfen Kontrol Ediniz.",
                     "result": {"durumu": false}}""");

            ResponseEntity<JsonNode> response = callGet("/authorized-service/device-completed/abc");

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            JsonNode problem = response.getBody();
            assertThat(problem.get("code").asText()).isEqualTo("DEVICE_GPS_TIME_MISSING");
            assertThat(problem.get("result").get("status").asBoolean())
                    .as("data arriving with a failure must not be dropped, and must be translated")
                    .isFalse();
        }

        @Test
        void shouldCarryThePayloadForTheOtherCompletionPreconditionToo() {
            // The same endpoint refuses for more than one reason. Both refusals are 409 and
            // both arrive with a populated result, so neither may lose its payload.
            stubGet(
                    "/yetkiliservis/cihaztamamlandi/abc",
                    """
                    {"success": false, "type": "ERROR",
                     "message": "Kontak Aktif Durumda. Lütfen Kapatınız.",
                     "result": {"durumu": false}}""");

            ResponseEntity<JsonNode> response = callGet("/authorized-service/device-completed/abc");

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            JsonNode problem = response.getBody();
            assertThat(problem.get("code").asText()).isEqualTo("IGNITION_ACTIVE");
            assertThat(problem.get("result").get("status").asBoolean()).isFalse();
        }

        @Test
        void shouldSeparateAnUnfixableDeviceFaultFromAPrecondition() {
            // "Contact the technical support team" means nobody in the request path can put
            // this right, so it must not read as a 409 the caller could resolve and retry.
            LEGACY.stubFor(
                    put(urlPathEqualTo("/yetkiliservis/immobilizercheck/abc"))
                            .willReturn(
                                    aResponse()
                                            .withStatus(201)
                                            .withHeader("Content-Type", "application/json")
                                            .withBody(
                                                    failure(
                                                            "Vendor Bilgisinde Hata. Lütfen Teknik"
                                                                    + " Destek Ekibi ile"
                                                                    + " Görüşünüz."))));

            ResponseEntity<JsonNode> response =
                    send(
                            HttpMethod.PUT,
                            "/authorized-service/immobilizer-check/abc",
                            """
                            {"status": true}""",
                            authenticated());

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
            assertThat(response.getBody().get("code").asText())
                    .isEqualTo("DEVICE_VENDOR_DATA_INVALID");
        }

        @Test
        void shouldCatalogueTheGuidExceptionRatherThanLeakItAsUnrecognised() {
            // A .NET exception text, and the evidence that every {detailId} is a GUID.
            stubGet(
                    "/yetkiliservis/servistalepsatiri/abc",
                    failure(
                            "Guid should contain 32 digits with 4 dashes"
                                    + " (xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx)."));

            ResponseEntity<JsonNode> response =
                    callGet("/authorized-service/service-request-line/abc");

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody().get("code").asText())
                    .isEqualTo("INVALID_IDENTIFIER_FORMAT");
        }

        @Test
        void shouldReportAnUnrecognisedLegacyErrorWithoutInventingAMeaning() {
            stubGet("/yetkiliservis/servistalepsatiri/abc", failure("Bilinmeyen bir hata olustu"));

            ResponseEntity<JsonNode> response =
                    callGet("/authorized-service/service-request-line/abc");

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody().get("code").asText())
                    .isEqualTo("UNRECOGNISED_LEGACY_ERROR");
            assertThat(response.getBody().get("legacyMessage").asText())
                    .isEqualTo("Bilinmeyen bir hata olustu");
        }

        @Test
        void shouldUseTheStatusTheCatalogAssignsRatherThanADefaultOne() {
            // A command the platform refuses is the caller's request being unprocessable,
            // not a malformed one — the catalog says 422 and that is what must come out.
            LEGACY.stubFor(
                    put(urlPathEqualTo("/yetkiliservis/sendcarcommand/abc"))
                            .willReturn(
                                    aResponse()
                                            .withStatus(201)
                                            .withHeader("Content-Type", "application/json")
                                            .withBody(
                                                    failure(
                                                            "notsupported.commands.iotplatform.error"))));

            ResponseEntity<JsonNode> response =
                    send(
                            HttpMethod.PUT,
                            "/authorized-service/send-car-command/abc",
                            """
                            {"command": "siren", "commandId": "1",
                             "status": "on", "statusId": "1"}""",
                            authenticated());

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
            assertThat(response.getBody().get("code").asText()).isEqualTo("COMMAND_NOT_SUPPORTED");
        }

        @Test
        void shouldRelayAScalarResultUnchanged() {
            stubGet(
                    "/yetkiliservis/DVRSonDurum/abc",
                    envelope("\"https://example.invalid/stream.m3u8\""));

            ResponseEntity<JsonNode> response =
                    callGet("/authorized-service/dvr-last-status/abc?channel=0");

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(result(response).asText()).isEqualTo("https://example.invalid/stream.m3u8");
            LEGACY.verify(
                    getRequestedFor(urlPathEqualTo("/yetkiliservis/DVRSonDurum/abc"))
                            .withHeader("channel", equalTo("0")));
        }
    }

    // ============================================================ inbound guards

    @Nested
    @DisplayName("inbound guards")
    class InboundGuards {

        @Test
        void shouldRejectABodyThatIsNotJson() {
            HttpHeaders headers = authenticated();
            headers.setContentType(MediaType.TEXT_XML);

            ResponseEntity<JsonNode> response =
                    send(
                            HttpMethod.PUT,
                            "/authorized-service/service-request-line/abc",
                            "<line><plate>X</plate></line>",
                            headers);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
            assertThat(response.getBody().get("code").asText()).isEqualTo("UNSUPPORTED_MEDIA_TYPE");
        }

        @Test
        void shouldRejectABodyLargerThanTheConfiguredLimit() {
            String oversized =
                    """
                    {"plate": "%s"}"""
                            .formatted("X".repeat(5 * 1024));

            ResponseEntity<JsonNode> response =
                    send(
                            HttpMethod.PUT,
                            "/authorized-service/service-request-line/abc",
                            oversized,
                            authenticated());

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
            assertThat(response.getBody().get("code").asText()).isEqualTo("PAYLOAD_TOO_LARGE");
        }

        @Test
        void shouldRefuseToAnswerInAFormatItCannotProduce() {
            HttpHeaders headers = authenticated();
            headers.setAccept(java.util.List.of(MediaType.APPLICATION_XML));

            ResponseEntity<String> response =
                    http.exchange(
                            "/authorized-service/service-request-line/abc",
                            HttpMethod.GET,
                            new HttpEntity<>(null, headers),
                            String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_ACCEPTABLE);
            LEGACY.verify(
                    0, getRequestedFor(urlPathEqualTo("/yetkiliservis/servistalepsatiri/abc")));
        }

        @Test
        void shouldRejectABodyThatIsNotValidJson() {
            ResponseEntity<JsonNode> response =
                    send(
                            HttpMethod.PUT,
                            "/authorized-service/service-request-line/abc",
                            "{not json",
                            authenticated());

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody().get("code").asText()).isEqualTo("MALFORMED_REQUEST_BODY");
        }
    }

    // ============================================================ upstream faults

    @Nested
    @DisplayName("upstream faults")
    class UpstreamFaults {

        @Test
        void shouldRecogniseABotProtectionChallengeInsteadOfBlamingTheApi() {
            LEGACY.stubFor(
                    get(urlPathEqualTo("/yetkiliservis/servistalepsatiri/abc"))
                            .willReturn(
                                    aResponse()
                                            .withStatus(403)
                                            .withHeader("Content-Type", "text/html")
                                            .withBody(
                                                    "<!DOCTYPE html><html><head><title>Just a moment...</title>"
                                                            + "<script src=\"https://challenges.cloudflare.com/turnstile/v0/api.js\">"
                                                            + "</script></head><body></body></html>")));

            ResponseEntity<JsonNode> response =
                    callGet("/authorized-service/service-request-line/abc");

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
            assertThat(response.getBody().get("code").asText()).isEqualTo("UPSTREAM_CHALLENGE");
        }

        @Test
        void shouldDiagnoseARedirectSeparatelyBecauseItsFixIsDifferent() {
            LEGACY.stubFor(
                    get(urlPathEqualTo("/yetkiliservis/servistalepsatiri/abc"))
                            .willReturn(
                                    aResponse()
                                            .withStatus(302)
                                            .withHeader("Location", "https://elsewhere.invalid/")));

            ResponseEntity<JsonNode> response =
                    callGet("/authorized-service/service-request-line/abc");

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
            assertThat(response.getBody().get("code").asText()).isEqualTo("UPSTREAM_REDIRECT");
        }

        @Test
        void shouldRejectTheAspNetErrorBodyThatBypassesTheEnvelope() {
            LEGACY.stubFor(
                    put(urlPathEqualTo("/yetkiliservis/servistalepsatiri/abc"))
                            .willReturn(
                                    aResponse()
                                            .withStatus(405)
                                            .withHeader("Content-Type", "application/json")
                                            .withBody(
                                                    """
                                                    {"Message": "The requested resource does not support http method 'PUT'."}""")));

            ResponseEntity<JsonNode> response =
                    send(
                            HttpMethod.PUT,
                            "/authorized-service/service-request-line/abc",
                            """
                            {"plate": null}""",
                            authenticated());

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
            assertThat(response.getBody().get("code").asText())
                    .isEqualTo("UPSTREAM_NOT_AN_ENVELOPE");
        }
    }

    // ==================================================================== routing

    @Nested
    @DisplayName("routing")
    class Routing {

        @Test
        void shouldAnswer404ForAPathNoResourceServes() {
            ResponseEntity<JsonNode> response =
                    callGet("/authorized-service/there-is-no-such-thing");

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(response.getBody().get("code").asText()).isEqualTo("NO_SUCH_ENDPOINT");
        }

        @Test
        void shouldAnswerAProblemDocumentOutsideTheApiPrefixToo() {
            ResponseEntity<JsonNode> response = callGet("/nothing-here");

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(response.getBody().get("code").asText())
                    .as(
                            "every response this service gives is a problem document, not a container page")
                    .isEqualTo("NO_SUCH_ENDPOINT");
        }

        @Test
        void shouldAnswer405WithAnAllowHeaderWhenThePathExistsUnderAnotherMethod() {
            ResponseEntity<JsonNode> response =
                    send(
                            HttpMethod.DELETE,
                            "/authorized-service/service-request-line/abc",
                            null,
                            authenticated());

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
            assertThat(response.getHeaders().getAllow())
                    .containsExactlyInAnyOrder(HttpMethod.GET, HttpMethod.PUT);
        }
    }

    // =================================================================== coverage

    @Nested
    @DisplayName("mapping coverage")
    class Coverage {

        @Test
        void shouldReportAnEndpointThatHasNoResponseMappingAtAll() {
            // The largest gap of all, and the one the report used to miss: with no response
            // block the payload never reaches the translator, so nothing was observed and the
            // report said "clean" while the endpoint relayed Turkish field names untouched.
            stubGet(
                    "/yetkiliservis/servisdetay/abc",
                    envelope(
                            """
                            {"bakimdakicihaz": true, "kargodurumu": 1}"""));

            ResponseEntity<JsonNode> response = callGet("/authorized-service/service-detail/abc");

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(result(response).get("bakimdakicihaz").asBoolean())
                    .as("with no mapping the payload is relayed rather than dropped")
                    .isTrue();

            JsonNode coverage = callGet("/internal/mapping/coverage").getBody();
            assertThat(coverage.get("clean").asBoolean())
                    .as("an unmapped endpoint must not read as a clean mapping")
                    .isFalse();
            assertThat(coverage.get("findings").get("service-detail").toString())
                    .contains("no-response-mapping bakimdakicihaz")
                    .contains("no-response-mapping kargodurumu");
        }

        @Test
        void shouldReportAFieldTheLegacyApiStartsSending() {
            stubGet(
                    "/yetkiliservis/servistalepsatiri/abc",
                    envelope(
                            """
                            {"plaka": "00XXX000", "yepyeniAlan": 42}"""));

            ResponseEntity<JsonNode> response =
                    callGet("/authorized-service/service-request-line/abc");

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(result(response).get("yepyeniAlan").asInt())
                    .as("an unmapped field is relayed rather than lost")
                    .isEqualTo(42);

            ResponseEntity<JsonNode> coverage = callGet("/internal/mapping/coverage");
            assertThat(coverage.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(coverage.getBody().get("findings").get("work-order-line").toString())
                    .contains("yepyeniAlan");
        }
    }
}
