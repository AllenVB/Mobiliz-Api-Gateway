package com.mobiliz.ysbridge.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.mobiliz.ysbridge.mapping.loader.MappingConfigurationException;
import com.mobiliz.ysbridge.mapping.model.MappingCatalog;
import com.mobiliz.ysbridge.mapping.model.ResourceMapping;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Import;

/**
 * Startup behaviour of the mapping loader. The point of these tests is that a broken mapping
 * document must stop the service from starting.
 */
class MappingConfigurationTest {

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner()
                    .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
                    .withUserConfiguration(TestConfiguration.class)
                    .withPropertyValues("ys-bridge.legacy.base-url=http://localhost:0");

    @Test
    void shouldLoadTheMappingDocumentsShippedWithTheApplication() {
        runner.run(
                context -> {
                    assertThat(context).hasSingleBean(MappingCatalog.class);
                    assertThat(context.getBean(MappingCatalog.class).resources())
                            .extracting(ResourceMapping::name)
                            .contains("auth-login", "auth-password-reset-request");
                });
    }

    @Test
    void shouldRefuseToStartWhenAMappingDocumentHasATypo() {
        runner.withPropertyValues("ys-bridge.mapping.locations=classpath*:broken-mapping/*.yaml")
                .run(
                        context -> {
                            assertThat(context).hasFailed();
                            assertThat(context.getStartupFailure())
                                    .rootCause()
                                    .isInstanceOf(MappingConfigurationException.class)
                                    .hasMessageContaining("typo.yaml")
                                    .hasMessageContaining("broken-resource")
                                    .hasMessageContaining("responce");
                        });
    }

    @Test
    void shouldRefuseToStartWhenNoMappingDocumentIsFound() {
        runner.withPropertyValues("ys-bridge.mapping.locations=classpath*:no-such-directory/*.yaml")
                .run(
                        context -> {
                            assertThat(context).hasFailed();
                            assertThat(context.getStartupFailure())
                                    .rootCause()
                                    .isInstanceOf(MappingConfigurationException.class)
                                    .hasMessageContaining("matched no mapping documents");
                        });
    }

    @Test
    void shouldRefuseToStartWhenTheLegacyBaseUrlIsNotConfigured() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
                .withUserConfiguration(TestConfiguration.class)
                .run(
                        context -> {
                            assertThat(context).hasFailed();
                            // The constraint violation is reported by the property binder,
                            // several causes below the bean-creation failure.
                            assertThat(context.getStartupFailure())
                                    .hasStackTraceContaining(
                                            "ys-bridge.legacy.base-url must be set");
                        });
    }

    @EnableConfigurationProperties(BridgeProperties.class)
    @Import(MappingConfiguration.class)
    static class TestConfiguration {}
}
