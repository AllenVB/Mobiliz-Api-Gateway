package com.mobiliz.ysbridge.mapping.model;

import java.util.Objects;

/**
 * A header the legacy API expects, sourced from a query parameter of the exposed request.
 *
 * <p>The legacy API takes in headers values that belong in the URL: a search term (`search`), a
 * camera channel (`channel`). Exposing those as headers would carry the awkwardness forward, so the
 * bridge exposes ordinary query parameters and moves them into headers on the way out. This is the
 * anti-corruption layer doing its job.
 *
 * @param legacyName header name the legacy API reads
 * @param fromQuery query parameter name on the exposed request
 * @param required whether the exposed request is rejected when the parameter is absent
 */
public record LegacyHeaderMapping(String legacyName, String fromQuery, boolean required) {

    public LegacyHeaderMapping {
        Objects.requireNonNull(legacyName, "legacyName");
        Objects.requireNonNull(fromQuery, "fromQuery");
        if (legacyName.isBlank()) {
            throw new IllegalArgumentException("legacy header name must not be blank");
        }
        if (fromQuery.isBlank()) {
            throw new IllegalArgumentException(
                    "header '%s' must name the query parameter it comes from"
                            .formatted(legacyName));
        }
    }
}
