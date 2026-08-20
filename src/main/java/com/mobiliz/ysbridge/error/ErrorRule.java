package com.mobiliz.ysbridge.error;

import java.util.Objects;

/**
 * One entry of the error catalog: a legacy message and the exposed error it becomes.
 *
 * @param match legacy message, matched exactly after trimming
 * @param status HTTP status the bridge answers with
 * @param code stable machine-readable code for callers to branch on
 * @param title human-readable English summary
 */
public record ErrorRule(String match, int status, String code, String title) {

    public ErrorRule {
        Objects.requireNonNull(match, "match");
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(title, "title");
        if (match.isBlank()) {
            throw new IllegalArgumentException("an error rule must declare the message it matches");
        }
        if (status < 400 || status > 599) {
            throw new IllegalArgumentException(
                    "error rule '%s': status %d is not an error status".formatted(code, status));
        }
    }
}
