package com.mobiliz.ysbridge.mapping.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.mobiliz.ysbridge.mapping.UnmappedPolicy;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FieldMappingTest {

    @Test
    void shouldReportLeafAndGroupingForNestedExposedPath() {
        FieldMapping field =
                new FieldMapping(
                        "androidversion",
                        List.of("appVersions", "authorizedServiceAndroid"),
                        null,
                        false,
                        null,
                        null);

        assertThat(field.exposedLeaf()).isEqualTo("authorizedServiceAndroid");
        assertThat(field.isGrouped()).isTrue();
        assertThat(field.exposedPathAsString()).isEqualTo("appVersions.authorizedServiceAndroid");
    }

    @Test
    void shouldNotReportGroupingForFlatRename() {
        FieldMapping field = FieldMapping.rename("adSoyad", "fullName");

        assertThat(field.isGrouped()).isFalse();
        assertThat(field.exposedLeaf()).isEqualTo("fullName");
    }

    @Test
    void shouldRejectBlankLegacyName() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> FieldMapping.rename("  ", "fullName"))
                .withMessageContaining("must not be blank");
    }

    @Test
    void shouldRejectEmptyExposedPath() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new FieldMapping("adSoyad", List.of(), null, false, null, null))
                .withMessageContaining("must declare an exposed name");
    }

    @Test
    void shouldRejectBlankSegmentInExposedPath() {
        assertThatIllegalArgumentException()
                .isThrownBy(
                        () ->
                                new FieldMapping(
                                        "adSoyad", List.of("a", " "), null, false, null, null))
                .withMessageContaining("blank segment");
    }

    @Test
    void shouldRejectValuesCombinedWithNested() {
        ValueMapping values = ValueMapping.of(Map.of("AKTIF", "ACTIVE"));
        ObjectMapping nested =
                ObjectMapping.of(UnmappedPolicy.DROP, FieldMapping.rename("ilAdi", "city"));

        assertThatIllegalArgumentException()
                .isThrownBy(
                        () ->
                                new FieldMapping(
                                        "durum", List.of("status"), null, false, values, nested))
                .withMessageContaining("cannot declare both 'values' and 'nested'");
    }

    @Test
    void shouldRejectTypeCombinedWithNested() {
        ObjectMapping nested =
                ObjectMapping.of(UnmappedPolicy.DROP, FieldMapping.rename("ilAdi", "city"));

        assertThatIllegalArgumentException()
                .isThrownBy(
                        () ->
                                new FieldMapping(
                                        "adres",
                                        List.of("address"),
                                        FieldType.STRING,
                                        false,
                                        null,
                                        nested))
                .withMessageContaining("cannot declare both 'type' and 'nested'");
    }
}
