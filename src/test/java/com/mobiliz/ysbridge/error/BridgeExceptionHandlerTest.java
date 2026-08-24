package com.mobiliz.ysbridge.error;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * The last-resort handler, tested directly because it exists for situations no test can arrange on
 * purpose: a bug, an unforeseen library failure, anything nobody predicted.
 *
 * <p>What matters is not that it returns 500 — that is easy — but that it says nothing about why.
 * An unhandled exception is the single most likely way for an internal detail to reach a caller, so
 * this asserts the message does not travel.
 */
class BridgeExceptionHandlerTest {

    private final BridgeExceptionHandler handler = new BridgeExceptionHandler();

    /** Everything the caller could read, concatenated — nothing may be missed by looking at one. */
    private static String everythingTheCallerSees(ResponseEntity<JsonNode> response) {
        return String.valueOf(response.getBody());
    }

    @Test
    void shouldAnswer500ForAnythingUnforeseen() {
        ResponseEntity<JsonNode> response =
                handler.handleUnexpected(new IllegalStateException("boom"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        JsonNode body = response.getBody();
        assertThat(body.get("success").asBoolean())
                .as("the envelope must report the failure as well as the status")
                .isFalse();
        assertThat(body.get("code").asText()).isEqualTo("INTERNAL_ERROR");
        assertThat(body.get("result").isNull()).isTrue();
    }

    @Test
    void shouldNotDiscloseWhatWentWrongInternally() {
        RuntimeException withSecrets =
                new RuntimeException(
                        "connection to jdbc:postgresql://internal-db:5432 failed for user svc_admin");

        assertThat(everythingTheCallerSees(handler.handleUnexpected(withSecrets)))
                .as("an unhandled exception must not become a disclosure")
                .doesNotContain("jdbc")
                .doesNotContain("internal-db")
                .doesNotContain("svc_admin");
    }

    @Test
    void shouldNotDiscloseTheCauseChainEither() {
        Exception nested =
                new IllegalStateException(
                        "outer", new IllegalArgumentException("token=SECRET-VALUE-0001"));

        assertThat(everythingTheCallerSees(handler.handleUnexpected(nested)))
                .doesNotContain("SECRET-VALUE-0001");
    }
}
