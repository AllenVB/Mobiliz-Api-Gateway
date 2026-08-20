package com.mobiliz.ysbridge.mapping.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mobiliz.ysbridge.mapping.Direction;
import com.mobiliz.ysbridge.mapping.UnmappedPolicy;
import com.mobiliz.ysbridge.mapping.model.FieldMapping;
import com.mobiliz.ysbridge.mapping.model.FieldType;
import com.mobiliz.ysbridge.mapping.model.ObjectMapping;
import com.mobiliz.ysbridge.mapping.model.ValueMapping;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class JsonTranslatorTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final JsonTranslator translator = new JsonTranslator();
    private final RecordingObserver observer = new RecordingObserver();

    private static JsonNode json(String raw) {
        try {
            return JSON.readTree(raw);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("test fixture is not valid JSON", e);
        }
    }

    private static FieldMapping grouped(String legacyName, String... exposedPath) {
        return new FieldMapping(legacyName, List.of(exposedPath), null, false, null, null);
    }

    private JsonNode toEnglish(JsonNode source, ObjectMapping mapping) {
        return translator.translate(source, mapping, Direction.TO_ENGLISH, observer);
    }

    private JsonNode toLegacy(JsonNode source, ObjectMapping mapping) {
        return translator.translate(source, mapping, Direction.TO_LEGACY, observer);
    }

    @Nested
    class Renaming {

        private final ObjectMapping mapping =
                ObjectMapping.of(
                        UnmappedPolicy.DROP,
                        FieldMapping.rename("adSoyad", "fullName"),
                        FieldMapping.rename("rol", "role"));

        @Test
        void shouldRenameFieldsToEnglish() {
            JsonNode result =
                    toEnglish(
                            json(
                                    """
                    {"adSoyad": "Saha Destek 1", "rol": "1"}"""),
                            mapping);

            assertThat(result)
                    .isEqualTo(
                            json(
                                    """
                    {"fullName": "Saha Destek 1", "role": "1"}"""));
        }

        @Test
        void shouldRenameFieldsBackToLegacy() {
            JsonNode result =
                    toLegacy(
                            json(
                                    """
                    {"fullName": "Saha Destek 1", "role": "1"}"""),
                            mapping);

            assertThat(result)
                    .isEqualTo(
                            json(
                                    """
                    {"adSoyad": "Saha Destek 1", "rol": "1"}"""));
        }

        @Test
        void shouldOmitMappedFieldThatThePayloadDoesNotCarry() {
            JsonNode result =
                    toEnglish(
                            json(
                                    """
                    {"adSoyad": "Saha Destek 1"}"""),
                            mapping);

            assertThat(result)
                    .isEqualTo(
                            json(
                                    """
                    {"fullName": "Saha Destek 1"}"""));
        }
    }

    @Nested
    class Grouping {

        private final ObjectMapping mapping =
                ObjectMapping.of(
                        UnmappedPolicy.DROP,
                        grouped("androidversion", "appVersions", "android"),
                        grouped("iosversion", "appVersions", "ios"),
                        grouped("YSKVKK", "consents", "dataPrivacy"));

        @Test
        void shouldGroupFlatLegacyFieldsIntoNestedObjects() {
            JsonNode result =
                    toEnglish(
                            json(
                                    """
                                    {"androidversion": "2.0.3", "iosversion": "1.0(47)", "YSKVKK": true}"""),
                            mapping);

            assertThat(result)
                    .isEqualTo(
                            json(
                                    """
                                    {"appVersions": {"android": "2.0.3", "ios": "1.0(47)"},
                                     "consents": {"dataPrivacy": true}}"""));
        }

        @Test
        void shouldFlattenGroupedObjectsBackToLegacyFields() {
            JsonNode result =
                    toLegacy(
                            json(
                                    """
                                    {"appVersions": {"android": "2.0.3", "ios": "1.0(47)"},
                                     "consents": {"dataPrivacy": true}}"""),
                            mapping);

            assertThat(result)
                    .isEqualTo(
                            json(
                                    """
                                    {"androidversion": "2.0.3", "iosversion": "1.0(47)", "YSKVKK": true}"""));
        }

        @Test
        void shouldOmitGroupEntirelyWhenNoMemberIsPresent() {
            JsonNode result =
                    toEnglish(
                            json(
                                    """
                    {"YSKVKK": true}"""),
                            mapping);

            assertThat(result)
                    .isEqualTo(
                            json(
                                    """
                    {"consents": {"dataPrivacy": true}}"""));
        }
    }

    @Nested
    class NestedObjectsAndArrays {

        private final ObjectMapping address =
                ObjectMapping.of(
                        UnmappedPolicy.DROP,
                        FieldMapping.rename("ilAdi", "city"),
                        FieldMapping.rename("postaKodu", "postalCode"));

        private final ObjectMapping mapping =
                ObjectMapping.of(
                        UnmappedPolicy.DROP,
                        FieldMapping.rename("adSoyad", "fullName"),
                        new FieldMapping(
                                "adresBilgisi", List.of("address"), null, false, null, address));

        @Test
        void shouldTranslateNestedObject() {
            JsonNode result =
                    toEnglish(
                            json(
                                    """
                                    {"adSoyad": "Ali", "adresBilgisi": {"ilAdi": "Ankara", "postaKodu": "06500"}}"""),
                            mapping);

            assertThat(result)
                    .isEqualTo(
                            json(
                                    """
                                    {"fullName": "Ali", "address": {"city": "Ankara", "postalCode": "06500"}}"""));
        }

        @Test
        void shouldTranslateNestedObjectBackToLegacy() {
            JsonNode result =
                    toLegacy(
                            json(
                                    """
                                    {"fullName": "Ali", "address": {"city": "Ankara", "postalCode": "06500"}}"""),
                            mapping);

            assertThat(result)
                    .isEqualTo(
                            json(
                                    """
                                    {"adSoyad": "Ali", "adresBilgisi": {"ilAdi": "Ankara", "postaKodu": "06500"}}"""));
        }

        @Test
        void shouldTranslateEveryElementOfATopLevelArray() {
            ObjectMapping lines =
                    ObjectMapping.of(
                            UnmappedPolicy.DROP,
                            FieldMapping.rename("detayId", "detailId"),
                            FieldMapping.rename("plaka", "plateNumber"));

            JsonNode result =
                    toEnglish(
                            json(
                                    """
                                    [{"detayId": "A", "plaka": "06ABC01"},
                                     {"detayId": "B", "plaka": null}]"""),
                            lines);

            assertThat(result)
                    .isEqualTo(
                            json(
                                    """
                                    [{"detailId": "A", "plateNumber": "06ABC01"},
                                     {"detailId": "B", "plateNumber": null}]"""));
        }

        @Test
        void shouldTranslateNestedArrayOfObjects() {
            ObjectMapping mappingWithArray =
                    ObjectMapping.of(
                            UnmappedPolicy.DROP,
                            new FieldMapping(
                                    "satirlar", List.of("lines"), null, false, null, address));

            JsonNode result =
                    toEnglish(
                            json(
                                    """
                                    {"satirlar": [{"ilAdi": "Ankara"}, {"ilAdi": "Izmir"}]}"""),
                            mappingWithArray);

            assertThat(result)
                    .isEqualTo(
                            json(
                                    """
                            {"lines": [{"city": "Ankara"}, {"city": "Izmir"}]}"""));
        }

        @Test
        void shouldKeepNullWhereNestedObjectIsAbsent() {
            JsonNode result =
                    toEnglish(
                            json(
                                    """
                            {"adSoyad": "Ali", "adresBilgisi": null}"""),
                            mapping);

            assertThat(result)
                    .isEqualTo(
                            json(
                                    """
                    {"fullName": "Ali", "address": null}"""));
        }
    }

    @Nested
    class ValueTables {

        private final ObjectMapping mapping =
                ObjectMapping.of(
                        UnmappedPolicy.DROP,
                        new FieldMapping(
                                "durum",
                                List.of("status"),
                                null,
                                false,
                                ValueMapping.of(Map.of("AKTIF", "ACTIVE")),
                                null));

        @Test
        void shouldTranslateKnownValueInBothDirections() {
            assertThat(
                            toEnglish(
                                    json(
                                            """
                    {"durum": "AKTIF"}"""),
                                    mapping))
                    .isEqualTo(
                            json(
                                    """
                            {"status": "ACTIVE"}"""));
            assertThat(
                            toLegacy(
                                    json(
                                            """
                    {"status": "ACTIVE"}"""),
                                    mapping))
                    .isEqualTo(
                            json(
                                    """
                            {"durum": "AKTIF"}"""));
        }

        @Test
        void shouldPassUnknownValueThroughAndReportIt() {
            JsonNode result =
                    toEnglish(
                            json(
                                    """
                    {"durum": "BEKLIYOR"}"""),
                            mapping);

            assertThat(result)
                    .isEqualTo(
                            json(
                                    """
                    {"status": "BEKLIYOR"}"""));
            assertThat(observer.unmappedValues()).containsExactly("TO_ENGLISH durum=BEKLIYOR");
        }

        @Test
        void shouldLeaveNullUntouched() {
            assertThat(
                            toEnglish(
                                    json(
                                            """
                    {"durum": null}"""),
                                    mapping))
                    .isEqualTo(
                            json(
                                    """
                            {"status": null}"""));
            assertThat(observer.unmappedValues()).isEmpty();
        }
    }

    @Nested
    class TypeCoercion {

        private final ObjectMapping mapping =
                ObjectMapping.of(
                        UnmappedPolicy.DROP,
                        new FieldMapping(
                                "serino",
                                List.of("serialNumber"),
                                FieldType.INTEGER,
                                false,
                                null,
                                null));

        @Test
        void shouldTurnQuotedNumberIntoNumber() {
            assertThat(
                            toEnglish(
                                    json(
                                            """
                    {"serino": "1234567"}"""),
                                    mapping))
                    .isEqualTo(
                            json(
                                    """
                            {"serialNumber": 1234567}"""));
        }

        @Test
        void shouldRenderNumberBackAsStringForLegacy() {
            assertThat(
                            toLegacy(
                                    json(
                                            """
                    {"serialNumber": 1234567}"""),
                                    mapping))
                    .isEqualTo(
                            json(
                                    """
                            {"serino": "1234567"}"""));
        }

        @Test
        void shouldKeepNullAsNull() {
            assertThat(
                            toEnglish(
                                    json(
                                            """
                    {"serino": null}"""),
                                    mapping))
                    .isEqualTo(
                            json(
                                    """
                            {"serialNumber": null}"""));
        }

        @Test
        void shouldPassUncoercibleValueThroughAndReportIt() {
            JsonNode result =
                    toEnglish(
                            json(
                                    """
                    {"serino": "N/A"}"""),
                            mapping);

            assertThat(result)
                    .isEqualTo(
                            json(
                                    """
                    {"serialNumber": "N/A"}"""));
            assertThat(observer.typeMismatches())
                    .containsExactly("TO_ENGLISH serino=N/A expected INTEGER");
        }

        @Test
        void shouldCoerceQuotedBooleanAndRenderItBack() {
            ObjectMapping booleanMapping =
                    ObjectMapping.of(
                            UnmappedPolicy.DROP,
                            new FieldMapping(
                                    "aktif",
                                    List.of("active"),
                                    FieldType.BOOLEAN,
                                    false,
                                    null,
                                    null));

            assertThat(
                            toEnglish(
                                    json(
                                            """
                    {"aktif": "1"}"""),
                                    booleanMapping))
                    .isEqualTo(
                            json(
                                    """
                            {"active": true}"""));
            assertThat(
                            toLegacy(
                                    json(
                                            """
                    {"active": true}"""),
                                    booleanMapping))
                    .isEqualTo(
                            json(
                                    """
                            {"aktif": "true"}"""));
        }
    }

    @Nested
    class EmptyStringNormalisation {

        private final ObjectMapping mapping =
                ObjectMapping.of(
                        UnmappedPolicy.DROP,
                        new FieldMapping(
                                "montajDokumani",
                                List.of("installationDocument"),
                                null,
                                true,
                                null,
                                null));

        @Test
        void shouldTurnEmptyStringIntoNull() {
            assertThat(
                            toEnglish(
                                    json(
                                            """
                    {"montajDokumani": ""}"""),
                                    mapping))
                    .isEqualTo(
                            json(
                                    """
                            {"installationDocument": null}"""));
        }

        @Test
        void shouldTurnNullBackIntoEmptyString() {
            assertThat(
                            toLegacy(
                                    json(
                                            """
                    {"installationDocument": null}"""),
                                    mapping))
                    .isEqualTo(
                            json(
                                    """
                            {"montajDokumani": ""}"""));
        }

        @Test
        void shouldLeaveNonEmptyStringUntouched() {
            assertThat(
                            toEnglish(
                                    json(
                                            """
                    {"montajDokumani": "doc.pdf"}"""),
                                    mapping))
                    .isEqualTo(
                            json(
                                    """
                            {"installationDocument": "doc.pdf"}"""));
        }
    }

    @Nested
    class UnmappedFields {

        private static FieldMapping known() {
            return FieldMapping.rename("adSoyad", "fullName");
        }

        @Test
        void shouldPassUnknownLegacyFieldThroughAndReportIt() {
            JsonNode result =
                    toEnglish(
                            json(
                                    """
                                    {"adSoyad": "Ali", "yeniAlan": 42}"""),
                            ObjectMapping.of(UnmappedPolicy.PASSTHROUGH_AND_REPORT, known()));

            assertThat(result)
                    .isEqualTo(
                            json(
                                    """
                    {"fullName": "Ali", "yeniAlan": 42}"""));
            assertThat(observer.unmappedFields()).containsExactly("TO_ENGLISH yeniAlan");
        }

        @Test
        void shouldDropUnknownFieldSilently() {
            JsonNode result =
                    toEnglish(
                            json(
                                    """
                                    {"adSoyad": "Ali", "yeniAlan": 42}"""),
                            ObjectMapping.of(UnmappedPolicy.DROP, known()));

            assertThat(result)
                    .isEqualTo(
                            json(
                                    """
                    {"fullName": "Ali"}"""));
            assertThat(observer.unmappedFields()).isEmpty();
        }

        @Test
        void shouldRejectUnknownFieldWhenPolicyIsFail() {
            ObjectMapping mapping = ObjectMapping.of(UnmappedPolicy.FAIL, known());

            assertThatThrownBy(
                            () ->
                                    toEnglish(
                                            json(
                                                    """
                    {"yeniAlan": 42}"""),
                                            mapping))
                    .isInstanceOf(UnmappedFieldException.class)
                    .hasMessageContaining("yeniAlan");
        }

        @Test
        void shouldRejectUnknownFieldInInboundRequest() {
            ObjectMapping mapping =
                    ObjectMapping.of(
                            UnmappedPolicy.FAIL, FieldMapping.rename("kullaniciAdi", "username"));

            assertThatThrownBy(
                            () ->
                                    toLegacy(
                                            json(
                                                    """
                                    {"username": "x", "surname": "y"}"""),
                                            mapping))
                    .isInstanceOf(UnmappedFieldException.class)
                    .hasMessageContaining("surname");
        }

        @Test
        void shouldNotReportGroupingObjectAsUnmapped() {
            ObjectMapping mapping =
                    ObjectMapping.of(
                            UnmappedPolicy.FAIL,
                            grouped("androidversion", "appVersions", "android"));

            JsonNode result =
                    toLegacy(
                            json(
                                    """
                    {"appVersions": {"android": "2.0.3"}}"""),
                            mapping);

            assertThat(result)
                    .isEqualTo(
                            json(
                                    """
                    {"androidversion": "2.0.3"}"""));
        }

        @Test
        void shouldRejectUnknownMemberInsideAGroupingObject() {
            ObjectMapping mapping =
                    ObjectMapping.of(
                            UnmappedPolicy.FAIL,
                            grouped("androidversion", "appVersions", "android"));

            assertThatThrownBy(
                            () ->
                                    toLegacy(
                                            json(
                                                    """
                                                    {"appVersions": {"android": "2.0.3", "windows": "1.0"}}"""),
                                            mapping))
                    .isInstanceOf(UnmappedFieldException.class)
                    .hasMessageContaining("appVersions.windows");
        }
    }

    @Nested
    class StructuralErrors {

        @Test
        void shouldPassAScalarResultThroughAndReportTheShape() {
            // DVRSonDurum answers with a bare URL string as its whole result.
            ObjectMapping mapping =
                    ObjectMapping.of(
                            UnmappedPolicy.DROP, FieldMapping.rename("adSoyad", "fullName"));

            JsonNode result = toEnglish(json("\"https://example.invalid/stream.m3u8\""), mapping);

            assertThat(result.asText()).isEqualTo("https://example.invalid/stream.m3u8");
            assertThat(observer.unexpectedShapes()).containsExactly("TO_ENGLISH <root> is STRING");
        }

        @Test
        void shouldReturnNullNodeForNullInput() {
            ObjectMapping mapping =
                    ObjectMapping.of(
                            UnmappedPolicy.DROP, FieldMapping.rename("adSoyad", "fullName"));

            assertThat(translator.translate(null, mapping, Direction.TO_ENGLISH, observer).isNull())
                    .isTrue();
        }
    }
}
