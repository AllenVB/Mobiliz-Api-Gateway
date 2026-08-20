package com.mobiliz.ysbridge;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.mobiliz.ysbridge.config.BridgeProperties;
import com.mobiliz.ysbridge.error.ErrorCatalog;
import com.mobiliz.ysbridge.error.ErrorRule;
import com.mobiliz.ysbridge.gateway.PageRequest;
import com.mobiliz.ysbridge.mapping.model.LegacyHeaderMapping;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The guards that exist so a misconfiguration cannot start.
 *
 * <p>Gathered here because they share a purpose rather than a package: each one refuses to build an
 * object that would misbehave later. A guard nobody has exercised is a guard nobody knows works,
 * which is the same as not having one.
 */
class GuardRailsTest {

    @Nested
    class PagingSettings {

        @Test
        void shouldRejectADefaultPageSizeLargerThanTheMaximum() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new BridgeProperties.Pagination(200, 100))
                    .withMessageContaining("must not exceed max-size");
        }

        @Test
        void shouldRejectANegativePageNumber() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new PageRequest(-1, 10))
                    .withMessageContaining("must not be negative");
        }

        @Test
        void shouldRejectAnEmptyPage() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new PageRequest(0, 0))
                    .withMessageContaining("at least 1");
        }
    }

    @Nested
    class LegacyHeaders {

        @Test
        void shouldRejectAHeaderWithNoName() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new LegacyHeaderMapping("  ", "serialNumber", true))
                    .withMessageContaining("must not be blank");
        }

        @Test
        void shouldRejectAHeaderWithNoSource() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new LegacyHeaderMapping("search", " ", true))
                    .withMessageContaining("must name the query parameter");
        }
    }

    @Nested
    class ErrorCatalogEntries {

        @Test
        void shouldRejectARuleThatMatchesNothing() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new ErrorRule("  ", 400, "CODE", "Title"))
                    .withMessageContaining("must declare the message it matches");
        }

        @Test
        void shouldRejectARuleWhoseStatusIsNotAnError() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new ErrorRule("boom", 200, "CODE", "Title"))
                    .withMessageContaining("is not an error status");
        }

        @Test
        void shouldRejectTwoRulesCompetingForTheSameMessage() {
            ErrorRule first = new ErrorRule("Token Bilgisi Eksik", 401, "FIRST", "First");
            ErrorRule second = new ErrorRule("Token Bilgisi Eksik", 403, "SECOND", "Second");

            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new ErrorCatalog(List.of(first, second)))
                    .withMessageContaining("match the same message");
        }
    }
}
