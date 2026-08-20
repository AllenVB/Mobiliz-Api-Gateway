package com.mobiliz.ysbridge.mapping.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.mobiliz.ysbridge.mapping.UnmappedPolicy;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ObjectMappingTest {

    private static FieldMapping grouped(String legacyName, String... exposedPath) {
        return new FieldMapping(legacyName, List.of(exposedPath), null, false, null, null);
    }

    @Nested
    class Lookup {

        @Test
        void shouldFindFieldByLegacyNameAndByExposedPath() {
            FieldMapping android = grouped("androidversion", "appVersions", "android");
            ObjectMapping mapping =
                    ObjectMapping.of(
                            UnmappedPolicy.PASSTHROUGH_AND_REPORT,
                            FieldMapping.rename("adSoyad", "fullName"),
                            android);

            assertThat(mapping.byLegacyName("adSoyad"))
                    .map(FieldMapping::exposedLeaf)
                    .contains("fullName");
            assertThat(mapping.byExposedPath(List.of("appVersions", "android"))).contains(android);
        }

        @Test
        void shouldReturnEmptyForUnknownField() {
            ObjectMapping mapping =
                    ObjectMapping.of(
                            UnmappedPolicy.DROP, FieldMapping.rename("adSoyad", "fullName"));

            assertThat(mapping.byLegacyName("bilinmeyen")).isEmpty();
            assertThat(mapping.byExposedPath(List.of("unknown"))).isEmpty();
        }
    }

    @Nested
    class Validation {

        @Test
        void shouldRejectSameLegacyFieldMappedTwice() {
            assertThatIllegalArgumentException()
                    .isThrownBy(
                            () ->
                                    ObjectMapping.of(
                                            UnmappedPolicy.DROP,
                                            FieldMapping.rename("durum", "status"),
                                            FieldMapping.rename("durum", "state")))
                    .withMessageContaining("is mapped twice");
        }

        @Test
        void shouldRejectTwoLegacyFieldsMappingToTheSameExposedPath() {
            assertThatIllegalArgumentException()
                    .isThrownBy(
                            () ->
                                    ObjectMapping.of(
                                            UnmappedPolicy.DROP,
                                            FieldMapping.rename("durum", "status"),
                                            FieldMapping.rename("hal", "status")))
                    .withMessageContaining("both map to exposed path 'status'");
        }

        @Test
        void shouldRejectExposedPathNestedUnderAnotherFieldsValue() {
            assertThatIllegalArgumentException()
                    .isThrownBy(
                            () ->
                                    ObjectMapping.of(
                                            UnmappedPolicy.DROP,
                                            FieldMapping.rename("surumler", "appVersions"),
                                            grouped("androidversion", "appVersions", "android")))
                    .withMessageContaining("is nested under");
        }
    }
}
