package com.mobiliz.ysbridge;

import static org.assertj.core.api.Assertions.assertThat;

import com.mobiliz.ysbridge.mapping.model.MappingCatalog;
import com.mobiliz.ysbridge.mapping.model.ResourceMapping;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

@SpringBootTest(properties = "ys-bridge.legacy.base-url=http://localhost:0")
class YsBridgeApplicationTest {

    private final ApplicationContext context;

    YsBridgeApplicationTest(ApplicationContext context) {
        this.context = context;
    }

    @Test
    void shouldLoadApplicationContextWithConfiguredName() {
        assertThat(context.getEnvironment().getProperty("spring.application.name"))
                .isEqualTo("ys-bridge");
    }

    @Test
    void shouldLoadTheMappingCatalogFromTheClasspathAtStartup() {
        MappingCatalog catalog = context.getBean(MappingCatalog.class);

        assertThat(catalog.resources())
                .extracting(ResourceMapping::name)
                .contains("auth-login", "auth-password-reset-request");
    }
}
