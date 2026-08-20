package com.mobiliz.ysbridge;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mobiliz.ysbridge.gateway.RouteResolver;
import com.mobiliz.ysbridge.mapping.Direction;
import com.mobiliz.ysbridge.mapping.engine.JsonTranslator;
import com.mobiliz.ysbridge.mapping.engine.TranslationObserver;
import com.mobiliz.ysbridge.mapping.loader.MappingYamlLoader;
import com.mobiliz.ysbridge.mapping.model.MappingCatalog;
import com.mobiliz.ysbridge.mapping.model.ObjectMapping;
import com.mobiliz.ysbridge.mapping.model.ResourceMapping;
import java.io.IOException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

/**
 * The two promises this service is built on, made executable.
 *
 * <p>Both are claims anyone can repeat in a meeting — "adding an endpoint is configuration, not
 * code" and "renaming a field cannot break anything". A claim nobody checks is a claim that quietly
 * stops being true. These tests fail the moment either one does.
 */
class ConfigDrivenGuaranteesTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final MappingYamlLoader loader = new MappingYamlLoader();
    private final JsonTranslator translator = new JsonTranslator();

    private static JsonNode json(String raw) {
        try {
            return JSON.readTree(raw);
        } catch (IOException e) {
            throw new IllegalArgumentException("test fixture is not valid JSON", e);
        }
    }

    private ResourceMapping only(String yaml) {
        return loader.parse(yaml, "test.yaml").getFirst();
    }

    @Nested
    class AddingAnEndpointNeedsNoCode {

        /** An endpoint that exists in no shipped mapping document and in no Java class. */
        private static final String BRAND_NEW =
                """
                resources:
                  something-that-did-not-exist:
                    method: GET
                    exposedPath: /api/v1/invented-resource/{someId}
                    legacyPath: /yetkiliservis/hicboylebirsey/{someId}
                    response:
                      fields:
                        uydurmaAlan: inventedField
                        digerUydurma: nested.other
                """;

        @Test
        void shouldRouteAnEndpointThatOnlyExistsInConfiguration() {
            MappingCatalog catalog = new MappingCatalog(loader.parse(BRAND_NEW, "test.yaml"));
            RouteResolver resolver = new RouteResolver(catalog);

            assertThat(resolver.resolve(HttpMethod.GET, "/api/v1/invented-resource/42"))
                    .as("a route declared only in YAML must be servable without a Java change")
                    .isPresent()
                    .get()
                    .satisfies(
                            match -> {
                                assertThat(match.resource().name())
                                        .isEqualTo("something-that-did-not-exist");
                                assertThat(match.pathVariables()).containsEntry("someId", "42");
                            });
        }

        @Test
        void shouldTranslateAnEndpointThatOnlyExistsInConfiguration() {
            ObjectMapping mapping = only(BRAND_NEW).response();

            JsonNode translated =
                    translator.translate(
                            json(
                                    """
                                    {"uydurmaAlan": 1, "digerUydurma": "x"}"""),
                            mapping,
                            Direction.TO_ENGLISH,
                            TranslationObserver.noop());

            assertThat(translated)
                    .isEqualTo(
                            json(
                                    """
                    {"inventedField": 1, "nested": {"other": "x"}}"""));
        }

        @Test
        void shouldNotNeedTheMethodToBeKnownInAdvance() {
            // Every method the loader accepts is servable by the same generic handler.
            for (String method : new String[] {"GET", "POST", "PUT", "PATCH", "DELETE"}) {
                ResourceMapping resource =
                        only(
                                """
                                resources:
                                  r:
                                    method: %s
                                    exposedPath: /api/v1/x
                                    legacyPath: /yetkiliservis/x
                                """
                                        .formatted(method));

                assertThat(
                                new RouteResolver(new MappingCatalog(java.util.List.of(resource)))
                                        .resolve(HttpMethod.valueOf(method), "/api/v1/x"))
                        .as("method %s", method)
                        .isPresent();
            }
        }
    }

    @Nested
    class RenamingAFieldBreaksNothing {

        /** A legacy payload. What it is called on the way out is not its business. */
        private static final String LEGACY_PAYLOAD =
                """
                {"adSoyad": "Saha Destek 1", "rol": "1", "rolAciklama": "test", "qr": false}""";

        private static final String NAMING_A =
                """
                resources:
                  r:
                    method: GET
                    exposedPath: /api/v1/x
                    legacyPath: /yetkiliservis/x
                    response:
                      fields:
                        adSoyad: fullName
                        rol: role.code
                        rolAciklama: role.description
                        qr: qrEnabled
                """;

        /** The same mapping with every exposed name chosen differently. */
        private static final String NAMING_B =
                """
                resources:
                  r:
                    method: GET
                    exposedPath: /api/v1/x
                    legacyPath: /yetkiliservis/x
                    response:
                      fields:
                        adSoyad: displayName
                        rol: authorisation.level
                        rolAciklama: authorisation.label
                        qr: qrCodeAllowed
                """;

        private JsonNode toEnglish(String yaml) {
            return translator.translate(
                    json(LEGACY_PAYLOAD),
                    only(yaml).response(),
                    Direction.TO_ENGLISH,
                    TranslationObserver.noop());
        }

        private JsonNode backToLegacy(String yaml, JsonNode exposed) {
            return translator.translate(
                    exposed,
                    only(yaml).response(),
                    Direction.TO_LEGACY,
                    TranslationObserver.noop());
        }

        @Test
        void shouldProduceDifferentNamesButTheSameLegacyPayload() {
            JsonNode exposedA = toEnglish(NAMING_A);
            JsonNode exposedB = toEnglish(NAMING_B);

            assertThat(exposedA)
                    .as("the two namings must genuinely differ, or this test proves nothing")
                    .isNotEqualTo(exposedB);

            assertThat(backToLegacy(NAMING_A, exposedA))
                    .as("what the legacy API receives cannot depend on the names we chose")
                    .isEqualTo(json(LEGACY_PAYLOAD))
                    .isEqualTo(backToLegacy(NAMING_B, exposedB));
        }

        @Test
        void shouldCoverThePayloadCompletelyUnderEitherNaming() {
            for (String yaml : new String[] {NAMING_A, NAMING_B}) {
                CountingObserver gaps = new CountingObserver();
                translator.translate(
                        json(LEGACY_PAYLOAD), only(yaml).response(), Direction.TO_ENGLISH, gaps);

                assertThat(gaps.count)
                        .as("coverage is a property of the mapping, not of the names")
                        .isZero();
            }
        }
    }

    /** Counts anything the translator could not fully handle. */
    private static final class CountingObserver implements TranslationObserver {

        private int count;

        @Override
        public void unmappedField(Direction direction, String path) {
            count++;
        }

        @Override
        public void unmappedValue(Direction direction, String path, String value) {
            count++;
        }

        @Override
        public void typeMismatch(
                Direction direction,
                String path,
                String value,
                com.mobiliz.ysbridge.mapping.model.FieldType expected) {
            count++;
        }

        @Override
        public void unexpectedShape(Direction direction, String path, String actualType) {
            count++;
        }
    }
}
