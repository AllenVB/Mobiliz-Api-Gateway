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
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.http.HttpMethod;

/**
 * Drives the complete mapping catalog — every document the application ships — against the payloads
 * captured from the legacy API, with identifiers and personal data masked.
 *
 * <p>Assertions are deliberately <em>name-independent</em>: they check that the mapping covers
 * every field the legacy API actually sends and that a payload translated out and back reproduces
 * itself exactly. Both stay true after the team replaces the provisional English names, so this
 * suite is the safety net for that rename.
 */
class CapturedPayloadsTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final JsonTranslator translator = new JsonTranslator();
    private final MappingCatalog catalog = loadEveryMappingDocument();

    /** Loads the catalog exactly as {@code MappingConfiguration} does at startup. */
    private static MappingCatalog loadEveryMappingDocument() {
        try {
            Resource[] resources =
                    new PathMatchingResourcePatternResolver()
                            .getResources("classpath*:mapping/*.yaml");
            MappingYamlLoader loader = new MappingYamlLoader();
            List<ResourceMapping> mappings = new ArrayList<>();
            for (Resource resource :
                    List.of(resources).stream()
                            .sorted(Comparator.comparing(r -> String.valueOf(r.getFilename())))
                            .toList()) {
                mappings.addAll(
                        loader.parse(
                                resource.getContentAsString(StandardCharsets.UTF_8),
                                String.valueOf(resource.getFilename())));
            }
            return new MappingCatalog(mappings);
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

    private ResourceMapping resource(String name) {
        return catalog.byName(name)
                .orElseThrow(() -> new AssertionError("no such resource in the catalog: " + name));
    }

    /** Asserts the mapping covers the payload completely, and returns the translation. */
    private JsonNode assertFullyCovers(String payload, ObjectMapping mapping, Direction direction) {
        Gaps gaps = new Gaps();
        JsonNode translated = translator.translate(json(payload), mapping, direction, gaps);

        assertThat(gaps.gaps).as("every field of the captured payload must be mapped").isEmpty();
        return translated;
    }

    private void assertRoundTrips(String payload, ObjectMapping mapping) {
        JsonNode original = json(payload);
        JsonNode exposed =
                translator.translate(
                        original, mapping, Direction.TO_ENGLISH, TranslationObserver.noop());
        JsonNode backToLegacy =
                translator.translate(
                        exposed, mapping, Direction.TO_LEGACY, TranslationObserver.noop());

        assertThat(backToLegacy).isEqualTo(original);
    }

    // =================================================================== inventory

    @Nested
    class CatalogInventory {

        @Test
        void shouldDeclareEveryEndpointCapturedSoFar() {
            assertThat(catalog.resources())
                    .extracting(ResourceMapping::name)
                    .containsExactlyInAnyOrder(
                            // auth.yaml
                            "auth-login",
                            "auth-password-reset-request",
                            // work-order.yaml
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
                            "vehicle-search",
                            // car-command.yaml
                            "immobilizer-status",
                            "immobilizer-update",
                            "send-car-command",
                            "send-status-command",
                            // reference-data.yaml
                            "dropdown-list",
                            // service.yaml
                            "service-detail",
                            "service-detail-update",
                            "service-maintenance-update",
                            // document-and-file.yaml
                            "accessory-document",
                            "file-upload",
                            // user.yaml
                            "user-consents-update");
        }

        @Test
        void shouldExposeEveryResourceUnderTheAuthorizedServicePrefix() {
            assertThat(catalog.resources())
                    .allSatisfy(
                            r ->
                                    assertThat(r.exposedPath())
                                            .as("resource %s", r.name())
                                            .startsWith("/authorized-service/"));
        }

        @Test
        void shouldRouteEveryResourceToTheLegacyServicePrefix() {
            assertThat(catalog.resources())
                    .allSatisfy(
                            r ->
                                    assertThat(r.legacyPath())
                                            .as("resource %s", r.name())
                                            .startsWith("/yetkiliservis/"));
        }

        @Test
        void shouldAllowTwoResourcesToShareOneLegacyEndpointWhenTheBodiesDiffer() {
            // The legacy API tells service-detail-update and service-maintenance-update
            // apart only by which fields the body carries. The bridge gives them separate
            // exposed paths so callers cannot conflate them.
            assertThat(resource("service-detail-update").legacyPath())
                    .isEqualTo(resource("service-maintenance-update").legacyPath());
            assertThat(resource("service-detail-update").exposedPath())
                    .isNotEqualTo(resource("service-maintenance-update").exposedPath());
        }

        @Test
        void shouldOnlyRelayAnUntranslatedBodyForTheOneEndpointWhoseShapeIsUnknown() {
            List<String> writesWithoutARequestMapping =
                    catalog.resources().stream()
                            .filter(r -> r.method() != HttpMethod.GET)
                            .filter(r -> r.request() == null)
                            .map(ResourceMapping::name)
                            .sorted()
                            .toList();

            assertThat(writesWithoutARequestMapping)
                    .as(
                            "a write that relays its body untranslated defeats the point of the bridge; "
                                    + "only send-status-command may, because its body was never captured")
                    .containsExactly("send-status-command");
        }

        @Test
        void shouldRejectUnknownFieldsOnEveryMappedRequest() {
            assertThat(catalog.resources())
                    .filteredOn(r -> r.request() != null)
                    .allSatisfy(
                            r ->
                                    assertThat(r.request().unmapped())
                                            .as(
                                                    "resource %s: an unrecognised field in an inbound request "
                                                            + "means the caller has the wrong contract",
                                                    r.name())
                                            .isEqualTo(UnmappedPolicy.FAIL));
        }

        @Test
        void shouldReportRatherThanDropUnknownFieldsOnEveryMappedResponse() {
            assertThat(catalog.resources())
                    .filteredOn(r -> r.response() != null)
                    .allSatisfy(
                            r ->
                                    assertThat(r.response().unmapped())
                                            .as(
                                                    "resource %s: a field the legacy API adds later must surface, "
                                                            + "not vanish",
                                                    r.name())
                                            .isEqualTo(UnmappedPolicy.PASSTHROUGH_AND_REPORT));
        }
    }

    // ================================================================== car command

    @Nested
    class CarCommand {

        private static final String IMMOBILIZER_STATUS =
                """
                {"durum": false}""";

        private static final String CAR_COMMAND_REQUEST =
                """
                {"komut": "siren", "komutid": "1", "durum": "on", "durumid": "1"}""";

        @Test
        void shouldMapTheImmobilizerStatus() {
            JsonNode exposed =
                    assertFullyCovers(
                            IMMOBILIZER_STATUS,
                            resource("immobilizer-status").response(),
                            Direction.TO_ENGLISH);

            assertThat(exposed.has("durum")).as("the legacy name must not survive").isFalse();
            assertRoundTrips(IMMOBILIZER_STATUS, resource("immobilizer-status").response());
        }

        @Test
        void shouldMapTheImmobilizerUpdateRequest() {
            assertFullyCovers(
                    IMMOBILIZER_STATUS,
                    resource("immobilizer-update").request(),
                    Direction.TO_ENGLISH);
        }

        @Test
        void shouldMapTheCarCommandRequestIncludingBothLabelAndCodePairs() {
            assertFullyCovers(
                    CAR_COMMAND_REQUEST,
                    resource("send-car-command").request(),
                    Direction.TO_ENGLISH);
            assertRoundTrips(CAR_COMMAND_REQUEST, resource("send-car-command").request());
        }

        @Test
        void shouldLeaveTheStatusCommandBodyUnmappedUntilItIsCaptured() {
            assertThat(resource("send-status-command").request())
                    .as("its body was never captured; guessing one would be worse than relaying it")
                    .isNull();
        }
    }

    // =============================================================== reference data

    @Nested
    class ReferenceData {

        private static final String DROPDOWN_ENTRIES =
                """
                [ { "comboAdi": "par_gsmcompany", "key": "Avea", "value": "100000002",
                    "parentvalue": null, "regardingvalue": null },
                  { "comboAdi": "par_gsmcompany", "key": "Turkcell", "value": "100000000",
                    "parentvalue": null, "regardingvalue": null } ]
                """;

        @Test
        void shouldMapEveryFieldOfADropdownEntry() {
            JsonNode exposed =
                    assertFullyCovers(
                            DROPDOWN_ENTRIES,
                            resource("dropdown-list").response(),
                            Direction.TO_ENGLISH);

            assertThat(exposed).hasSize(2);
        }

        @Test
        void shouldRoundTripTheDropdownEntries() {
            assertRoundTrips(DROPDOWN_ENTRIES, resource("dropdown-list").response());
        }
    }

    // ======================================================================= service

    @Nested
    class Service {

        private static final String SERVICE_DETAIL_UPDATE =
                """
                { "bakimdakicihaz": true,
                  "bakimdakikablotesisat": false,
                  "bakimdakigsm": true,
                  "sorununkaynagi": "<fault>",
                  "sorununkaynagidetay": "<fault detail>",
                  "cozumdetay": "<resolution>",
                  "resimyukle": true,
                  "kargodurumu": 1,
                  "bakimdakiaksesuar1": "<accessory>",
                  "bakimdakiaksesuar2": null,
                  "bakimdakiaksesuar3": null }
                """;

        private static final String MAINTENANCE_UPDATE =
                """
                {"kargofirmasi": "1", "kargofirmasidiger": "", "kargotakipno": "000000000"}""";

        @Test
        void shouldMapEveryFieldOfTheServiceDetailUpdate() {
            assertFullyCovers(
                    SERVICE_DETAIL_UPDATE,
                    resource("service-detail-update").request(),
                    Direction.TO_ENGLISH);
        }

        @Test
        void shouldRoundTripTheServiceDetailUpdate() {
            assertRoundTrips(SERVICE_DETAIL_UPDATE, resource("service-detail-update").request());
        }

        @Test
        void shouldMapEveryFieldOfTheMaintenanceUpdate() {
            assertFullyCovers(
                    MAINTENANCE_UPDATE,
                    resource("service-maintenance-update").request(),
                    Direction.TO_ENGLISH);
        }

        @Test
        void shouldRoundTripTheMaintenanceUpdateIncludingItsEmptyStringField() {
            // kargofirmasidiger arrives as "" and is exposed as null; the round trip must
            // put the empty string back rather than sending null to the legacy API.
            assertRoundTrips(MAINTENANCE_UPDATE, resource("service-maintenance-update").request());
        }
    }

    // ============================================================ document and file

    @Nested
    class DocumentAndFile {

        private static final String ACCESSORY_DOCUMENT_REQUEST =
                """
                {"aksesuarId": "123", "aksesuarAdi": "<accessory>",
                 "detayid": "456", "durumu": false}""";

        private static final String ACCESSORY_DOCUMENT_RESPONSE =
                """
                {"link": "https://crm.example.invalid/Folder/masked_document.pdf"}""";

        private static final String FILE_UPLOAD_REQUEST =
                """
                {"icerik": "QkFTRTY0", "mimeType": "image/jpeg", "dosyaAdi": "masked.jpg"}""";

        @Test
        void shouldMapTheAccessoryDocumentRequestIncludingItsLowerCaseIdKey() {
            assertFullyCovers(
                    ACCESSORY_DOCUMENT_REQUEST,
                    resource("accessory-document").request(),
                    Direction.TO_ENGLISH);
            assertRoundTrips(ACCESSORY_DOCUMENT_REQUEST, resource("accessory-document").request());
        }

        @Test
        void shouldMapTheDocumentLinkResponse() {
            JsonNode exposed =
                    assertFullyCovers(
                            ACCESSORY_DOCUMENT_RESPONSE,
                            resource("accessory-document").response(),
                            Direction.TO_ENGLISH);

            // `link` is both the legacy name and the agreed English one, so unlike every
            // other field here the check is that it survives rather than that it does not.
            assertThat(exposed.get("link").asText()).startsWith("https://");
        }

        @Test
        void shouldMapTheFileUploadRequest() {
            assertFullyCovers(
                    FILE_UPLOAD_REQUEST, resource("file-upload").request(), Direction.TO_ENGLISH);
            assertRoundTrips(FILE_UPLOAD_REQUEST, resource("file-upload").request());
        }
    }

    // ======================================================================= service

    @Nested
    class ServiceDetailRead {

        /** The first successful capture of servisdetay, taken 2026-08-24. */
        private static final String SERVICE_DETAIL_RESPONSE =
                """
                {
                  "cihaz": true, "kablotesisat": false, "gsm": false,
                  "varisegpsanten": true,
                  "bakimdakicihaz": true, "bakimdakikablotesisat": false,
                  "bakimdakigsm": true, "bakimdakivarisegpsanten": false,
                  "bakimdakiaksesuar1": null, "bakimdakiaksesuar2": null,
                  "bakimdakiaksesuar3": null,
                  "sorununkaynagi": null, "sorununkaynagidetay": null,
                  "cozumdetay": null, "resimyukle": false, "kargodurumu": 0
                }
                """;

        @Test
        void shouldTranslateTheElevenFieldsTheVocabularyNames() {
            JsonNode exposed =
                    translator.translate(
                            json(SERVICE_DETAIL_RESPONSE),
                            resource("service-detail").response(),
                            Direction.TO_ENGLISH,
                            TranslationObserver.noop());

            assertThat(exposed.get("deviceUnderMaintenance").asBoolean()).isTrue();
            assertThat(exposed.get("gsmUnderMaintenance").asBoolean()).isTrue();
            assertThat(exposed.get("cargoStatus").asInt()).isZero();
            assertThat(exposed.get("uploadImage").asBoolean()).isFalse();
        }

        @Test
        void shouldRelayAndReportTheFiveFieldsNobodyHasNamedYet() {
            Gaps gaps = new Gaps();

            JsonNode exposed =
                    translator.translate(
                            json(SERVICE_DETAIL_RESPONSE),
                            resource("service-detail").response(),
                            Direction.TO_ENGLISH,
                            gaps);

            // Relayed rather than dropped: an unnamed field is still the caller's data.
            assertThat(exposed.get("cihaz").asBoolean()).isTrue();
            assertThat(exposed.get("varisegpsanten").asBoolean()).isTrue();

            // And reported, so the gap is visible rather than quietly carried forever.
            assertThat(gaps.gaps)
                    .as("exactly the five fields with no agreed English name")
                    .hasSize(5)
                    .allMatch(
                            gap ->
                                    gap.contains("cihaz")
                                            || gap.contains("kablotesisat")
                                            || gap.contains("gsm")
                                            || gap.contains("varisegpsanten"));
        }

        @Test
        void shouldSurviveARoundTripDespiteTheUnnamedFields() {
            assertRoundTrips(SERVICE_DETAIL_RESPONSE, resource("service-detail").response());
        }
    }

    // ========================================================================== user

    @Nested
    class User {

        private static final String CONSENTS =
                """
                {"KVKK": true, "ETK": true}""";

        @Test
        void shouldMapBothConsentFlags() {
            assertFullyCovers(
                    CONSENTS, resource("user-consents-update").request(), Direction.TO_ENGLISH);
            assertRoundTrips(CONSENTS, resource("user-consents-update").request());
        }
    }

    /** Observer that records anything the mapping did not fully handle. */
    private static final class Gaps implements TranslationObserver {

        private final List<String> gaps = new ArrayList<>();

        @Override
        public void unmappedField(Direction direction, String path) {
            gaps.add("unmapped field " + path);
        }

        @Override
        public void unmappedValue(Direction direction, String path, String value) {
            gaps.add("unmapped value " + path);
        }

        @Override
        public void typeMismatch(
                Direction direction, String path, String value, FieldType expected) {
            gaps.add("type mismatch " + path);
        }

        @Override
        public void unexpectedShape(Direction direction, String path, String actualType) {
            gaps.add("unexpected shape " + path);
        }
    }
}
