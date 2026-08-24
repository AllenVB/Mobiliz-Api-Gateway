package com.mobiliz.ysbridge.mapping.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

class ResourceMappingTest {

    private static ResourceMapping resource(String exposedPath, String legacyPath) {
        return new ResourceMapping(
                "work-order-lines", HttpMethod.GET, exposedPath, legacyPath, null, null);
    }

    @Test
    void shouldAcceptTemplatesDeclaringTheSameVariables() {
        ResourceMapping mapping =
                resource(
                        "/authorized-service/service-request-lines/{workOrderId}",
                        "/yetkiliservis/servistalepsatirlari/{workOrderId}");

        assertThat(mapping.pathVariables()).containsExactly("workOrderId");
    }

    @Test
    void shouldRejectVariableThatExistsOnOneSideOnly() {
        assertThatIllegalArgumentException()
                .isThrownBy(
                        () ->
                                resource(
                                        "/authorized-service/service-request-lines/{workOrderId}",
                                        "/yetkiliservis/servistalepsatirlari/{detayId}"))
                .withMessageContaining("path variables differ");
    }

    @Test
    void shouldRejectPathThatIsNotAbsolute() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> resource("api/v1/lines", "/yetkiliservis/satirlar"))
                .withMessageContaining("must start with '/'");
    }

    @Test
    void shouldRejectVariableDeclaredTwiceInOneTemplate() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> resource("/api/{id}/x/{id}", "/legacy/{id}"))
                .withMessageContaining("more than once");
    }

    @Test
    void shouldRejectBlankName() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ResourceMapping(" ", HttpMethod.GET, "/a", "/b", null, null))
                .withMessageContaining("must not be blank");
    }
}
