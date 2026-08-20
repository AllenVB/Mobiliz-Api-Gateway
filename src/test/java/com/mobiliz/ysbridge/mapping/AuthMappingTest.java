package com.mobiliz.ysbridge.mapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mobiliz.ysbridge.mapping.engine.JsonTranslator;
import com.mobiliz.ysbridge.mapping.engine.TranslationObserver;
import com.mobiliz.ysbridge.mapping.engine.UnmappedFieldException;
import com.mobiliz.ysbridge.mapping.loader.MappingYamlLoader;
import com.mobiliz.ysbridge.mapping.model.MappingCatalog;
import com.mobiliz.ysbridge.mapping.model.ResourceMapping;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

/**
 * Exercises the real {@code mapping/auth.yaml} shipped with the application against payloads
 * captured from the legacy API, with credentials replaced by placeholders.
 *
 * <p>These are the tests that would catch a mistake in the mapping file itself, which unit tests
 * built from hand-written fixtures cannot do.
 */
class AuthMappingTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final JsonTranslator translator = new JsonTranslator();
    private final MappingCatalog catalog = loadCatalog();

    private static MappingCatalog loadCatalog() {
        try (InputStream stream = AuthMappingTest.class.getResourceAsStream("/mapping/auth.yaml")) {
            if (stream == null) {
                throw new IllegalStateException("mapping/auth.yaml is missing from the classpath");
            }
            String yaml = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            return new MappingCatalog(new MappingYamlLoader().parse(yaml, "mapping/auth.yaml"));
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

    private ResourceMapping login() {
        return catalog.byName("auth-login").orElseThrow();
    }

    /** The {@code result} of a real login response; the token is a placeholder. */
    private static final String LEGACY_LOGIN_RESULT =
            """
            {
              "token": "TEST-TOKEN-0000-0000",
              "adSoyad": "Saha Destek 1",
              "rol": "1",
              "rolAciklama": "test",
              "qr": false,
              "androidversion": "2.0.3",
              "iosversion": "1.0(47)",
              "satisandroidversion": "1.0.3",
              "satisiosversion": "1.0 (29)",
              "YSKVKK": true,
              "YSETK": true,
              "SatisKVKK": true,
              "SatisETK": true
            }
            """;

    private static final String EXPOSED_LOGIN_RESULT =
            """
            {
              "token": "TEST-TOKEN-0000-0000",
              "fullName": "Saha Destek 1",
              "role": {
                "code": "1",
                "description": "test"
              },
              "qrEnabled": false,
              "appVersions": {
                "authorizedServiceAndroid": "2.0.3",
                "authorizedServiceIos": "1.0(47)",
                "salesAndroid": "1.0.3",
                "salesIos": "1.0 (29)"
              },
              "consents": {
                "authorizedServiceDataPrivacy": true,
                "authorizedServiceMarketing": true,
                "salesDataPrivacy": true,
                "salesMarketing": true
              }
            }
            """;

    @Test
    void shouldDeclareBothAuthResources() {
        assertThat(catalog.size()).isEqualTo(2);
        assertThat(login().method()).isEqualTo(HttpMethod.POST);
        assertThat(login().legacyPath()).isEqualTo("/yetkiliservis/giris");
        assertThat(catalog.byName("auth-password-reset-request"))
                .get()
                .extracting(ResourceMapping::exposedPath)
                .isEqualTo("/api/v1/auth/password-reset-requests");
    }

    @Nested
    class LoginResponse {

        @Test
        void shouldTranslateCapturedLegacyResultIntoTheExposedContract() {
            JsonNode translated =
                    translator.translate(
                            json(LEGACY_LOGIN_RESULT),
                            login().response(),
                            Direction.TO_ENGLISH,
                            TranslationObserver.noop());

            assertThat(translated).isEqualTo(json(EXPOSED_LOGIN_RESULT));
        }

        @Test
        void shouldLeaveNoLegacyFieldUnmapped() {
            RecordingCounts counts = new RecordingCounts();

            translator.translate(
                    json(LEGACY_LOGIN_RESULT), login().response(), Direction.TO_ENGLISH, counts);

            assertThat(counts.unmapped)
                    .as("every field of the captured login response must be mapped")
                    .isEmpty();
        }

        @Test
        void shouldSurviveARoundTripBackToTheLegacyShape() {
            JsonNode exposed =
                    translator.translate(
                            json(LEGACY_LOGIN_RESULT),
                            login().response(),
                            Direction.TO_ENGLISH,
                            TranslationObserver.noop());

            JsonNode backToLegacy =
                    translator.translate(
                            exposed,
                            login().response(),
                            Direction.TO_LEGACY,
                            TranslationObserver.noop());

            assertThat(backToLegacy).isEqualTo(json(LEGACY_LOGIN_RESULT));
        }

        @Test
        void shouldReportAFieldTheLegacyApiAddsLater() {
            RecordingCounts counts = new RecordingCounts();
            JsonNode withNewField =
                    json(
                            """
                    {"adSoyad": "Saha Destek 1", "yeniAlan": "x"}""");

            JsonNode translated =
                    translator.translate(
                            withNewField, login().response(), Direction.TO_ENGLISH, counts);

            assertThat(translated.get("fullName").asText()).isEqualTo("Saha Destek 1");
            assertThat(translated.get("yeniAlan").asText())
                    .as("an unmapped field is passed through rather than lost")
                    .isEqualTo("x");
            assertThat(counts.unmapped).containsExactly("yeniAlan");
        }
    }

    @Nested
    class LoginRequest {

        @Test
        void shouldTranslateExposedRequestIntoTheLegacyShape() {
            JsonNode legacy =
                    translator.translate(
                            json(
                                    """
                                    {"username": "test-user", "password": "test-password",
                                     "platform": "IOS", "deviceToken": null}"""),
                            login().request(),
                            Direction.TO_LEGACY,
                            TranslationObserver.noop());

            assertThat(legacy)
                    .isEqualTo(
                            json(
                                    """
                                    {"kullaniciAdi": "test-user", "sifre": "test-password",
                                     "osversiyon": "IOS", "deviceToken": null}"""));
        }

        @Test
        void shouldRejectAFieldThatIsNotPartOfTheExposedContract() {
            JsonNode request =
                    json(
                            """
                            {"username": "test-user", "password": "test-password", "sifre": "oops"}""");

            assertThatThrownBy(
                            () ->
                                    translator.translate(
                                            request,
                                            login().request(),
                                            Direction.TO_LEGACY,
                                            TranslationObserver.noop()))
                    .isInstanceOf(UnmappedFieldException.class)
                    .hasMessageContaining("sifre");
        }
    }

    @Nested
    class PasswordResetRequest {

        @Test
        void shouldTranslateUsernameIntoTheLegacyField() {
            ResourceMapping resource = catalog.byName("auth-password-reset-request").orElseThrow();

            JsonNode legacy =
                    translator.translate(
                            json(
                                    """
                                    {"username": "test-user"}"""),
                            resource.request(),
                            Direction.TO_LEGACY,
                            TranslationObserver.noop());

            assertThat(legacy)
                    .isEqualTo(
                            json(
                                    """
                    {"kullaniciAdi": "test-user"}"""));
        }

        @Test
        void shouldHaveNoResponseMappingUntilARealSampleIsCaptured() {
            ResourceMapping resource = catalog.byName("auth-password-reset-request").orElseThrow();

            assertThat(resource.response())
                    .as(
                            "the only capture was intercepted by Cloudflare, so nothing is asserted yet")
                    .isNull();
        }
    }

    /** Minimal observer that only records unmapped field paths. */
    private static final class RecordingCounts implements TranslationObserver {

        private final java.util.List<String> unmapped = new java.util.ArrayList<>();

        @Override
        public void unmappedField(Direction direction, String path) {
            unmapped.add(path);
        }

        @Override
        public void unmappedValue(Direction direction, String path, String value) {
            // not asserted here
        }

        @Override
        public void typeMismatch(
                Direction direction,
                String path,
                String value,
                com.mobiliz.ysbridge.mapping.model.FieldType expected) {
            // not asserted here
        }

        @Override
        public void unexpectedShape(Direction direction, String path, String actualType) {
            // not asserted here
        }
    }
}
