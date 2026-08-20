package com.mobiliz.ysbridge.mapping.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.mobiliz.ysbridge.mapping.Direction;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ValueMappingTest {

    @Nested
    class Translation {

        @Test
        void shouldTranslateInBothDirections() {
            ValueMapping mapping = ValueMapping.of(Map.of("AKTIF", "ACTIVE"));

            assertThat(mapping.translate("AKTIF", Direction.TO_ENGLISH)).contains("ACTIVE");
            assertThat(mapping.translate("ACTIVE", Direction.TO_LEGACY)).contains("AKTIF");
        }

        @Test
        void shouldReturnEmptyWhenValueIsNotInTable() {
            ValueMapping mapping = ValueMapping.of(Map.of("AKTIF", "ACTIVE"));

            assertThat(mapping.translate("BEKLIYOR", Direction.TO_ENGLISH)).isEmpty();
            assertThat(mapping.translate("AKTIF", Direction.TO_LEGACY)).isEmpty();
        }
    }

    @Nested
    class Validation {

        @Test
        void shouldRejectAmbiguousReverseMappingWhenTwoLegacyValuesShareAnExposedValue() {
            Map<String, String> ambiguous = new LinkedHashMap<>();
            ambiguous.put("AKTIF", "ACTIVE");
            ambiguous.put("ACIK", "ACTIVE");

            assertThatIllegalArgumentException()
                    .isThrownBy(() -> ValueMapping.of(ambiguous))
                    .withMessageContaining("ambiguous reverse value mapping")
                    .withMessageContaining("ACTIVE");
        }

        @Test
        void shouldRejectEmptyTable() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> ValueMapping.of(Map.of()))
                    .withMessageContaining("at least one value");
        }

        @Test
        void shouldRejectBlankEntry() {
            Map<String, String> blank = new LinkedHashMap<>();
            blank.put("AKTIF", "  ");

            assertThatIllegalArgumentException()
                    .isThrownBy(() -> ValueMapping.of(blank))
                    .withMessageContaining("blank");
        }
    }
}
