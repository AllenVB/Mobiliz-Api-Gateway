package com.mobiliz.ysbridge.mapping.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

class PathTemplateTest {

    @Test
    void shouldExtractVariableNamesInDeclarationOrder() {
        assertThat(
                        PathTemplate.variableNames(
                                "/authorized-service/service-request-lines/{workOrderId}/{lineId}"))
                .containsExactly("workOrderId", "lineId");
    }

    @Test
    void shouldReturnEmptyListWhenTemplateHasNoVariables() {
        assertThat(PathTemplate.variableNames("/yetkiliservis/giris")).isEmpty();
    }

    @Test
    void shouldRejectUnclosedBrace() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> PathTemplate.variableNames("/api/{id"))
                .withMessageContaining("unbalanced '{'");
    }

    @Test
    void shouldRejectStrayClosingBrace() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> PathTemplate.variableNames("/api/id}"))
                .withMessageContaining("unbalanced '}'");
    }

    @Test
    void shouldRejectUnnamedVariable() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> PathTemplate.variableNames("/api/{}"))
                .withMessageContaining("malformed path variable");
    }
}
