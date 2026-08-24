package com.mobiliz.ysbridge.mapping;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mobiliz.ysbridge.mapping.engine.JsonTranslator;
import com.mobiliz.ysbridge.mapping.engine.TranslationObserver;
import com.mobiliz.ysbridge.mapping.loader.MappingYamlLoader;
import com.mobiliz.ysbridge.mapping.model.FieldType;
import com.mobiliz.ysbridge.mapping.model.MappingCatalog;
import com.mobiliz.ysbridge.mapping.model.ObjectMapping;
import com.mobiliz.ysbridge.mapping.model.ResourceMapping;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

/**
 * Exercises the real {@code mapping/work-order.yaml} against payloads captured from the legacy API,
 * with identifiers and personal data masked.
 *
 * <p>The exposed names in that file are still provisional, so these assertions are deliberately
 * <em>name-independent</em>: they check that the mapping covers every field the legacy API actually
 * sends, and that translating a payload out and back reproduces it exactly. Both remain true after
 * the team supplies the agreed vocabulary.
 */
class WorkOrderMappingTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final JsonTranslator translator = new JsonTranslator();
    private final MappingCatalog catalog = load("/mapping/work-order.yaml");

    private static MappingCatalog load(String resource) {
        try (InputStream stream = WorkOrderMappingTest.class.getResourceAsStream(resource)) {
            if (stream == null) {
                throw new IllegalStateException(resource + " is missing from the classpath");
            }
            String yaml = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            return new MappingCatalog(new MappingYamlLoader().parse(yaml, resource));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static JsonNode json(String raw) {
        try {
            return JSON.readTree(raw);
        } catch (IOException e) {
            throw new IllegalArgumentException("test fixture is not valid JSON", e);
        }
    }

    private ObjectMapping responseOf(String resourceName) {
        return catalog.byName(resourceName).orElseThrow().response();
    }

    private ObjectMapping requestOf(String resourceName) {
        return catalog.byName(resourceName).orElseThrow().request();
    }

    /** Translates and returns the paths of anything the mapping did not fully handle. */
    private Gaps translateAndCollectGaps(
            JsonNode payload, ObjectMapping mapping, Direction direction) {
        Gaps gaps = new Gaps();
        gaps.output = translator.translate(payload, mapping, direction, gaps);
        return gaps;
    }

    // ------------------------------------------------------------------ fixtures

    private static final String LINES =
            """
            [ { "detayId": "00000000-0000-0000-0000-000000000000",
                "srvNumarasi": "SRV-000000-00000",
                "plaka": null,
                "serino": "0000002",
                "vendorCode": "MT151",
                "gsm": null,
                "sonServisFotolari": "",
                "durum": null,
                "montajDokumani": "",
                "hizmetLokasyonu": 1,
                "sahadestekok": true,
                "bakimonarimok": false,
                "ysozelnot": null,
                "churn": false } ]
            """;

    private static final String LINE_DETAIL =
            """
            { "plaka": "00XXX000", "serino": "0000001", "gsm": "+900000000000",
              "canBusProfile": null, "vendorCode": "MT151", "stfFormNo": "ST-0000000",
              "telOperator": "100000002", "apnYok": false, "km": "87049",
              "testHatti": null,
              "aracTipi": "00000000-0000-0000-0000-000000000001",
              "yil": "804370052",
              "marka": "00000000-0000-0000-0000-000000000002",
              "model": "00000000-0000-0000-0000-000000000003",
              "motorHacmi": "4", "yakitTuru": "1", "hizmetLokasyonu": "1",
              "sehirIciDisi": null,
              "sehir": "00000000-0000-0000-0000-000000000004",
              "ilce": "00000000-0000-0000-0000-000000000005",
              "semt": null, "ysozelnot": null,
              "plakakontrolsuz": false, "yolbedelsiz": false, "churn": false,
              "lastikmarka": "5", "lastikebat": "134", "saseno": null,
              "otokocmu": false }
            """;

    private static final String VEHICLE_STATUS =
            """
            { "plaka": "00XXX000", "serino": "0000001", "gsm": "+900000000000",
              "sunucukaydi": true, "sonAdresBilgisi": "<masked address>",
              "ilkVeriTarihi": "2026-07-14T09:25:39",
              "sonVeriTarihi": "2026-08-20T08:12:57",
              "donanimBilgisi": null, "cihazYazilimVersiyonu": "60910",
              "mesafeSayaci": "5463", "anaBeslemeVoltaji": "13.24",
              "canbusYakitSeviyesi": null, "kontakAktivitesi": false,
              "canbusHiz": null, "canBusKM": null, "canbusok": true,
              "lastGPSTime": "2026-08-20T08:12:57",
              "canbusEngineRPM": null, "canbusFuelLevel": null,
              "canbusLastFuelUsed": null, "immobilizer": false,
              "canBusProfile": "", "emoveGsmNumber": "+900000000000",
              "lastBulkGSensorTime": null, "lastMultiGpsTime": null,
              "lastAccelerationandGpsTime": null,
              "lastTachoDriver1Cardno": null, "lastTachoDriver1Name": null,
              "lastCalibrationStatusDescription": null,
              "modularSensorTemprature": null, "tachomodel": null,
              "tachoversion": null, "serialTemperature": null, "DVR": false }
            """;

    private static final String VEHICLE_SEARCH =
            """
            [ { "id": "00000000-0000-0000-0000-000000000000",
                "SaseNo": "0X0XX0000XXX00000", "MotorNo": null,
                "AracTipi": "00000000-0000-0000-0000-000000000001",
                "Marka": "00000000-0000-0000-0000-000000000002",
                "Model": "00000000-0000-0000-0000-000000000003",
                "MotorHacmi": 0, "YakitTuru": 0, "Sene": 804370050,
                "LastikMarka": 0, "LastikEbat": 0 } ]
            """;

    private static final String COMPLETION_STATUS =
            """
            {"durumu": false}""";

    // ------------------------------------------------------------------- structure

    @Nested
    class Structure {

        @Test
        void shouldDeclareEveryCapturedWorkOrderEndpoint() {
            assertThat(catalog.resources())
                    .extracting(ResourceMapping::name)
                    .containsExactlyInAnyOrder(
                            "work-order-list",
                            "work-order-lines",
                            "work-order-line",
                            "work-order-line-update",
                            "vehicle-status",
                            "work-order-line-feedback",
                            "work-order-line-completion-status",
                            "work-order-line-demontage-completion",
                            "work-order-line-churn-completion",
                            "work-order-line-dvr-status",
                            "vehicle-search");
        }

        @Test
        void shouldServeReadAndWriteOfALineOnTheSamePathWithDifferentMethods() {
            assertThat(catalog.byName("work-order-line"))
                    .get()
                    .satisfies(
                            r -> {
                                assertThat(r.method()).isEqualTo(HttpMethod.GET);
                                assertThat(r.exposedPath())
                                        .isEqualTo(
                                                "/authorized-service/service-request-line/{detailId}");
                            });
            assertThat(catalog.byName("work-order-line-update"))
                    .get()
                    .satisfies(
                            r -> {
                                assertThat(r.method()).isEqualTo(HttpMethod.PUT);
                                assertThat(r.exposedPath())
                                        .isEqualTo(
                                                "/authorized-service/service-request-line/{detailId}");
                            });
        }

        @Test
        void shouldNotDeclareTheCompleteWorkOrderEndpointWhoseUrlIsUnknown() {
            assertThat(catalog.byName("work-order-line-completion"))
                    .as("its legacy URL is unconfirmed; guessing one would hide the gap")
                    .isEmpty();
        }

        @Test
        void shouldLeaveDvrStatusWithoutAResponseMappingBecauseItsResultIsAScalar() {
            assertThat(catalog.byName("work-order-line-dvr-status"))
                    .get()
                    .extracting(ResourceMapping::response)
                    .isNull();
        }
    }

    // -------------------------------------------------------------- coverage

    @Nested
    class CoverageOfCapturedPayloads {

        @Test
        void shouldMapEveryFieldOfTheWorkOrderLines() {
            Gaps gaps =
                    translateAndCollectGaps(
                            json(LINES), responseOf("work-order-lines"), Direction.TO_ENGLISH);

            assertThat(gaps.unmapped).isEmpty();
            assertThat(gaps.output).hasSize(1);
        }

        @Test
        void shouldMapEveryFieldOfTheLineDetail() {
            Gaps gaps =
                    translateAndCollectGaps(
                            json(LINE_DETAIL), responseOf("work-order-line"), Direction.TO_ENGLISH);

            assertThat(gaps.unmapped).isEmpty();
        }

        @Test
        void shouldMapEveryFieldOfTheVehicleStatus() {
            Gaps gaps =
                    translateAndCollectGaps(
                            json(VEHICLE_STATUS),
                            responseOf("vehicle-status"),
                            Direction.TO_ENGLISH);

            assertThat(gaps.unmapped).isEmpty();
        }

        @Test
        void shouldMapEveryFieldOfTheVehicleSearchResults() {
            Gaps gaps =
                    translateAndCollectGaps(
                            json(VEHICLE_SEARCH),
                            responseOf("vehicle-search"),
                            Direction.TO_ENGLISH);

            assertThat(gaps.unmapped).isEmpty();
        }

        @Test
        void shouldMapTheCompletionStatusPayloadThatArrivesWithAFailure() {
            Gaps gaps =
                    translateAndCollectGaps(
                            json(COMPLETION_STATUS),
                            responseOf("work-order-line-completion-status"),
                            Direction.TO_ENGLISH);

            assertThat(gaps.unmapped).isEmpty();
        }

        @Test
        void shouldMapEveryFieldOfTheLineUpdateRequest() {
            // The update request carries the same fields as the detail response.
            Gaps gaps =
                    translateAndCollectGaps(
                            json(LINE_DETAIL), responseOf("work-order-line"), Direction.TO_ENGLISH);
            JsonNode exposed = gaps.output;

            Gaps backwards =
                    translateAndCollectGaps(
                            exposed, requestOf("work-order-line-update"), Direction.TO_LEGACY);

            assertThat(backwards.unmapped)
                    .as("the update request must accept exactly what the read returns")
                    .isEmpty();
        }
    }

    // ------------------------------------------------------------- round trips

    @Nested
    class RoundTrips {

        private void assertRoundTrips(String payload, String resourceName) {
            ObjectMapping mapping = responseOf(resourceName);
            JsonNode original = json(payload);

            JsonNode exposed =
                    translator.translate(
                            original, mapping, Direction.TO_ENGLISH, TranslationObserver.noop());
            JsonNode backToLegacy =
                    translator.translate(
                            exposed, mapping, Direction.TO_LEGACY, TranslationObserver.noop());

            assertThat(backToLegacy).isEqualTo(original);
        }

        @Test
        void shouldRoundTripTheWorkOrderLines() {
            assertRoundTrips(LINES, "work-order-lines");
        }

        @Test
        void shouldRoundTripTheLineDetail() {
            assertRoundTrips(LINE_DETAIL, "work-order-line");
        }

        @Test
        void shouldRoundTripTheVehicleStatusIncludingItsGroupedBlocks() {
            assertRoundTrips(VEHICLE_STATUS, "vehicle-status");
        }

        @Test
        void shouldRoundTripTheVehicleSearchResults() {
            assertRoundTrips(VEHICLE_SEARCH, "vehicle-search");
        }
    }

    /** Observer that records gaps and keeps the translated output. */
    private static final class Gaps implements TranslationObserver {

        private final List<String> unmapped = new ArrayList<>();
        private JsonNode output;

        @Override
        public void unmappedField(Direction direction, String path) {
            unmapped.add(path);
        }

        @Override
        public void unmappedValue(Direction direction, String path, String value) {
            // provisional mappings declare no value tables yet
        }

        @Override
        public void typeMismatch(
                Direction direction, String path, String value, FieldType expected) {
            // provisional mappings declare no types yet
        }

        @Override
        public void unexpectedShape(Direction direction, String path, String actualType) {
            unmapped.add("<shape> " + path + " is " + actualType);
        }
    }
}
