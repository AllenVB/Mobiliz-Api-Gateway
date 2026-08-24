package com.mobiliz.ysbridge.mapping.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

class MappingCatalogTest {

    private static ResourceMapping resource(String name, HttpMethod method, String exposedPath) {
        return new ResourceMapping(name, method, exposedPath, "/yetkiliservis/x", null, null);
    }

    @Test
    void shouldIndexResourcesByName() {
        MappingCatalog catalog =
                new MappingCatalog(
                        List.of(
                                resource(
                                        "auth-login", HttpMethod.POST, "/authorized-service/login"),
                                resource("service-detail", HttpMethod.GET, "/api/v1/services")));

        assertThat(catalog.size()).isEqualTo(2);
        assertThat(catalog.byName("auth-login"))
                .map(ResourceMapping::method)
                .contains(HttpMethod.POST);
        assertThat(catalog.byName("missing")).isEmpty();
    }

    @Test
    void shouldAllowSamePathWithDifferentMethods() {
        MappingCatalog catalog =
                new MappingCatalog(
                        List.of(
                                resource("service-read", HttpMethod.GET, "/api/v1/services"),
                                resource("service-write", HttpMethod.PUT, "/api/v1/services")));

        assertThat(catalog.size()).isEqualTo(2);
    }

    @Test
    void shouldRejectDuplicateResourceName() {
        assertThatIllegalArgumentException()
                .isThrownBy(
                        () ->
                                new MappingCatalog(
                                        List.of(
                                                resource(
                                                        "auth-login", HttpMethod.POST, "/api/v1/a"),
                                                resource(
                                                        "auth-login",
                                                        HttpMethod.GET,
                                                        "/api/v1/b"))))
                .withMessageContaining("is defined twice");
    }

    @Test
    void shouldRejectTwoResourcesServingTheSameRoute() {
        assertThatIllegalArgumentException()
                .isThrownBy(
                        () ->
                                new MappingCatalog(
                                        List.of(
                                                resource(
                                                        "first",
                                                        HttpMethod.GET,
                                                        "/api/v1/services"),
                                                resource(
                                                        "second",
                                                        HttpMethod.GET,
                                                        "/api/v1/services"))))
                .withMessageContaining("both serve GET /api/v1/services");
    }
}
