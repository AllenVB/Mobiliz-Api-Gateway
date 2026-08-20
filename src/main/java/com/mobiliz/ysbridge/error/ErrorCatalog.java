package com.mobiliz.ysbridge.error;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Translates the legacy API's error messages into stable, English, machine-readable errors.
 *
 * <p>Legacy messages are free-form and arrive in at least four shapes — Turkish prose, a dotted
 * platform code, a raw .NET exception, and ASP.NET's own body. Only exact matches are translated;
 * anything else is passed through as an unrecognised business error, keeping the original text so
 * support can still trace it.
 *
 * <p>Deliberately <em>not</em> a fuzzy matcher. Guessing which error a half-matching message means
 * is how a bridge starts lying about what happened upstream.
 */
public final class ErrorCatalog {

    /** What an unmatched legacy error becomes. */
    public static final int UNRECOGNISED_STATUS = 400;

    public static final String UNRECOGNISED_CODE = "UNRECOGNISED_LEGACY_ERROR";
    public static final String UNRECOGNISED_TITLE = "The legacy service rejected the request";

    private final Map<String, ErrorRule> byMessage;

    public ErrorCatalog(List<ErrorRule> rules) {
        Objects.requireNonNull(rules, "rules");
        Map<String, ErrorRule> index = new LinkedHashMap<>();
        for (ErrorRule rule : rules) {
            ErrorRule duplicate = index.put(rule.match(), rule);
            if (duplicate != null) {
                throw new IllegalArgumentException(
                        "two error rules match the same message: '%s' and '%s'"
                                .formatted(duplicate.code(), rule.code()));
            }
        }
        this.byMessage = Map.copyOf(index);
    }

    /** Returns the rule for {@code legacyMessage}, or empty when it is not catalogued. */
    public Optional<ErrorRule> find(String legacyMessage) {
        if (legacyMessage == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(byMessage.get(legacyMessage.trim()));
    }

    public int size() {
        return byMessage.size();
    }
}
