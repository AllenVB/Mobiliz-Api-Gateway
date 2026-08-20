package com.mobiliz.ysbridge.error;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

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

    @Test
    void shouldAnswer500ForAnythingUnforeseen() {
        ProblemDetail problem = handler.handleUnexpected(new IllegalStateException("boom"));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
        assertThat(problem.getProperties()).containsEntry("code", "INTERNAL_ERROR");
    }

    @Test
    void shouldNotDiscloseWhatWentWrongInternally() {
        RuntimeException withSecrets =
                new RuntimeException(
                        "connection to jdbc:postgresql://internal-db:5432 failed for user svc_admin");

        ProblemDetail problem = handler.handleUnexpected(withSecrets);

        assertThat(problem.getDetail())
                .as("an unhandled exception must not become a disclosure")
                .doesNotContain("jdbc")
                .doesNotContain("internal-db")
                .doesNotContain("svc_admin");
        assertThat(problem.getTitle()).doesNotContain("svc_admin");
    }

    @Test
    void shouldNotDiscloseTheCauseChainEither() {
        Exception nested =
                new IllegalStateException(
                        "outer", new IllegalArgumentException("token=SECRET-VALUE-0001"));

        ProblemDetail problem = handler.handleUnexpected(nested);

        assertThat(String.valueOf(problem.getDetail()) + problem.getTitle())
                .doesNotContain("SECRET-VALUE-0001");
    }
}
