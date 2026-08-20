package com.mobiliz.ysbridge.mapping.loader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mobiliz.ysbridge.mapping.Direction;
import com.mobiliz.ysbridge.mapping.UnmappedPolicy;
import com.mobiliz.ysbridge.mapping.model.FieldMapping;
import com.mobiliz.ysbridge.mapping.model.FieldType;
import com.mobiliz.ysbridge.mapping.model.ObjectMapping;
import com.mobiliz.ysbridge.mapping.model.ResourceMapping;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

class MappingYamlLoaderTest {

    private final MappingYamlLoader loader = new MappingYamlLoader();

    private List<ResourceMapping> parse(String yaml) {
        return loader.parse(yaml, "test.yaml");
    }

    @Nested
    class HappyPath {

        private static final String AUTH_LOGIN =
                """
                resources:
                  auth-login:
                    method: POST
                    exposedPath: /api/v1/auth/login
                    legacyPath: /yetkiliservis/giris
                    request:
                      unmapped: FAIL
                      fields:
                        kullaniciAdi: username
                        sifre: password
                        osversiyon:
                          exposed: platform
                          values:
                            IOS: IOS
                            ANDROID: ANDROID
                    response:
                      fields:
                        token: token
                        adSoyad: fullName
                        androidversion: appVersions.authorizedServiceAndroid
                        YSKVKK: consents.authorizedServiceDataPrivacy
                        serino:
                          exposed: serialNumber
                          type: INTEGER
                        montajDokumani:
                          exposed: installationDocument
                          emptyStringAsNull: true
                """;

        @Test
        void shouldReadResourceHeader() {
            ResourceMapping resource = parse(AUTH_LOGIN).getFirst();

            assertThat(resource.name()).isEqualTo("auth-login");
            assertThat(resource.method()).isEqualTo(HttpMethod.POST);
            assertThat(resource.exposedPath()).isEqualTo("/api/v1/auth/login");
            assertThat(resource.legacyPath()).isEqualTo("/yetkiliservis/giris");
        }

        @Test
        void shouldReadShortFormFieldAsPlainRename() {
            ObjectMapping response = parse(AUTH_LOGIN).getFirst().response();

            assertThat(response.byLegacyName("adSoyad"))
                    .get()
                    .satisfies(
                            field -> {
                                assertThat(field.exposedLeaf()).isEqualTo("fullName");
                                assertThat(field.isGrouped()).isFalse();
                                assertThat(field.type()).isNull();
                                assertThat(field.emptyStringAsNull()).isFalse();
                            });
        }

        @Test
        void shouldSplitDottedExposedNameIntoGroupedPath() {
            ObjectMapping response = parse(AUTH_LOGIN).getFirst().response();

            assertThat(response.byLegacyName("androidversion"))
                    .get()
                    .extracting(FieldMapping::exposedPath)
                    .isEqualTo(List.of("appVersions", "authorizedServiceAndroid"));
        }

        @Test
        void shouldReadLongFormAttributes() {
            ObjectMapping response = parse(AUTH_LOGIN).getFirst().response();

            assertThat(response.byLegacyName("serino"))
                    .get()
                    .extracting(FieldMapping::type)
                    .isEqualTo(FieldType.INTEGER);
            assertThat(response.byLegacyName("montajDokumani"))
                    .get()
                    .extracting(FieldMapping::emptyStringAsNull)
                    .isEqualTo(true);
        }

        @Test
        void shouldReadValueTableInBothDirections() {
            ObjectMapping request = parse(AUTH_LOGIN).getFirst().request();

            FieldMapping platform = request.byLegacyName("osversiyon").orElseThrow();
            assertThat(platform.values().translate("IOS", Direction.TO_ENGLISH)).contains("IOS");
            assertThat(platform.values().translate("ANDROID", Direction.TO_LEGACY))
                    .contains("ANDROID");
        }

        @Test
        void shouldApplyDeclaredUnmappedPolicyAndDefaultToPassthrough() {
            ResourceMapping resource = parse(AUTH_LOGIN).getFirst();

            assertThat(resource.request().unmapped()).isEqualTo(UnmappedPolicy.FAIL);
            assertThat(resource.response().unmapped())
                    .isEqualTo(UnmappedPolicy.PASSTHROUGH_AND_REPORT);
        }

        @Test
        void shouldReadNestedObjectMapping() {
            List<ResourceMapping> resources =
                    parse(
                            """
                            resources:
                              customer:
                                method: GET
                                exposedPath: /api/v1/customers/{id}
                                legacyPath: /yetkiliservis/musteri/{id}
                                response:
                                  fields:
                                    adresBilgisi:
                                      exposed: address
                                      nested:
                                        unmapped: DROP
                                        fields:
                                          ilAdi: city
                                          postaKodu: postalCode
                            """);

            ObjectMapping nested =
                    resources
                            .getFirst()
                            .response()
                            .byLegacyName("adresBilgisi")
                            .orElseThrow()
                            .nested();

            assertThat(nested).isNotNull();
            assertThat(nested.unmapped()).isEqualTo(UnmappedPolicy.DROP);
            assertThat(nested.byLegacyName("ilAdi"))
                    .get()
                    .extracting(FieldMapping::exposedLeaf)
                    .isEqualTo("city");
        }

        @Test
        void shouldAllowResourceWithoutRequestOrResponseBlock() {
            ResourceMapping resource =
                    parse(
                                    """
                                    resources:
                                      ping:
                                        method: GET
                                        exposedPath: /api/v1/ping
                                        legacyPath: /yetkiliservis/ping
                                    """)
                            .getFirst();

            assertThat(resource.request()).isNull();
            assertThat(resource.response()).isNull();
        }
    }

    @Nested
    class Validation {

        @Test
        void shouldRejectUnknownKeyAndNameItsLocation() {
            assertThatThrownBy(
                            () ->
                                    parse(
                                            """
                                            resources:
                                              auth-login:
                                                method: POST
                                                exposedPath: /api/v1/auth/login
                                                legacyPath: /yetkiliservis/giris
                                                responce:
                                                  fields:
                                                    token: token
                                            """))
                    .isInstanceOf(MappingConfigurationException.class)
                    .hasMessageContaining("resources.auth-login")
                    .hasMessageContaining("unknown key(s) [responce]");
        }

        @Test
        void shouldRejectUnknownKeyInsideFieldDefinition() {
            assertThatThrownBy(
                            () ->
                                    parse(
                                            """
                                            resources:
                                              r:
                                                method: GET
                                                exposedPath: /api/v1/x
                                                legacyPath: /yetkiliservis/x
                                                response:
                                                  fields:
                                                    durum:
                                                      exposed: status
                                                      valuess:
                                                        AKTIF: ACTIVE
                                            """))
                    .isInstanceOf(MappingConfigurationException.class)
                    .hasMessageContaining("response.fields.durum")
                    .hasMessageContaining("valuess");
        }

        @Test
        void shouldRejectUnsupportedHttpMethod() {
            assertThatThrownBy(
                            () ->
                                    parse(
                                            """
                                            resources:
                                              r:
                                                method: FETCH
                                                exposedPath: /api/v1/x
                                                legacyPath: /yetkiliservis/x
                                            """))
                    .isInstanceOf(MappingConfigurationException.class)
                    .hasMessageContaining("r.method")
                    .hasMessageContaining("FETCH");
        }

        @Test
        void shouldRejectMissingLegacyPath() {
            assertThatThrownBy(
                            () ->
                                    parse(
                                            """
                                            resources:
                                              r:
                                                method: GET
                                                exposedPath: /api/v1/x
                                            """))
                    .isInstanceOf(MappingConfigurationException.class)
                    .hasMessageContaining("must declare 'legacyPath'");
        }

        @Test
        void shouldReportModelViolationWithYamlLocation() {
            assertThatThrownBy(
                            () ->
                                    parse(
                                            """
                                            resources:
                                              r:
                                                method: GET
                                                exposedPath: /api/v1/x
                                                legacyPath: /yetkiliservis/x
                                                response:
                                                  fields:
                                                    durum: status
                                                    hal: status
                                            """))
                    .isInstanceOf(MappingConfigurationException.class)
                    .hasMessageContaining("r.response")
                    .hasMessageContaining("both map to exposed path 'status'");
        }

        @Test
        void shouldReportAmbiguousValueTableWithYamlLocation() {
            assertThatThrownBy(
                            () ->
                                    parse(
                                            """
                                            resources:
                                              r:
                                                method: GET
                                                exposedPath: /api/v1/x
                                                legacyPath: /yetkiliservis/x
                                                response:
                                                  fields:
                                                    durum:
                                                      exposed: status
                                                      values:
                                                        AKTIF: ACTIVE
                                                        ACIK: ACTIVE
                                            """))
                    .isInstanceOf(MappingConfigurationException.class)
                    .hasMessageContaining("durum.values")
                    .hasMessageContaining("ambiguous reverse value mapping");
        }

        @Test
        void shouldRejectMismatchedPathVariablesWithYamlLocation() {
            assertThatThrownBy(
                            () ->
                                    parse(
                                            """
                                            resources:
                                              r:
                                                method: GET
                                                exposedPath: /api/v1/x/{workOrderId}
                                                legacyPath: /yetkiliservis/x/{detayId}
                                            """))
                    .isInstanceOf(MappingConfigurationException.class)
                    .hasMessageContaining("path variables differ");
        }

        @Test
        void shouldRejectMalformedYaml() {
            assertThatThrownBy(() -> parse("resources: [unclosed"))
                    .isInstanceOf(MappingConfigurationException.class)
                    .hasMessageContaining("is not valid YAML");
        }

        @Test
        void shouldRejectEmptyDocument() {
            assertThatThrownBy(() -> parse(""))
                    .isInstanceOf(MappingConfigurationException.class)
                    .hasMessageContaining("is empty");
        }

        @Test
        void shouldRejectDocumentWithoutResources() {
            assertThatThrownBy(() -> parse("glossary: {}"))
                    .isInstanceOf(MappingConfigurationException.class)
                    .hasMessageContaining("unknown key(s) [glossary]");
        }

        @Test
        void shouldRejectEmptyResourcesBlock() {
            assertThatThrownBy(() -> parse("resources: {}"))
                    .isInstanceOf(MappingConfigurationException.class)
                    .hasMessageContaining("at least one resource");
        }
    }
}
