package com.mobiliz.ysbridge.mapping.model;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.http.HttpMethod;

/**
 * One exposed endpoint and the legacy endpoint it delegates to.
 *
 * @param name identifier used in logs, metrics and the mapping file
 * @param method HTTP method, identical on both sides
 * @param exposedPath URI template this bridge serves, e.g. {@code /authorized-service/login}
 * @param legacyPath URI template on the legacy API, e.g. {@code /yetkiliservis/giris}
 * @param request mapping for the outbound request body, or {@code null} when there is none
 * @param response mapping for the inbound response payload, or {@code null}
 * @param legacyHeaders headers the legacy API expects, sourced from query parameters
 * @param authentication whether the caller must present a token
 */
public record ResourceMapping(
        String name,
        HttpMethod method,
        String exposedPath,
        String legacyPath,
        ObjectMapping request,
        ObjectMapping response,
        List<LegacyHeaderMapping> legacyHeaders,
        AuthenticationRequirement authentication) {

    public ResourceMapping {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(method, "method");
        Objects.requireNonNull(exposedPath, "exposedPath");
        Objects.requireNonNull(legacyPath, "legacyPath");
        legacyHeaders = List.copyOf(Objects.requireNonNull(legacyHeaders, "legacyHeaders"));
        Objects.requireNonNull(authentication, "authentication");
        if (name.isBlank()) {
            throw new IllegalArgumentException("resource name must not be blank");
        }
        requireAbsolute(name, "exposedPath", exposedPath);
        requireAbsolute(name, "legacyPath", legacyPath);
        requireMatchingVariables(name, exposedPath, legacyPath);
        requireDistinctHeaders(name, legacyHeaders);
    }

    /** Convenience overload: no legacy headers, and authentication required. */
    public ResourceMapping(
            String name,
            HttpMethod method,
            String exposedPath,
            String legacyPath,
            ObjectMapping request,
            ObjectMapping response) {
        this(
                name,
                method,
                exposedPath,
                legacyPath,
                request,
                response,
                List.of(),
                AuthenticationRequirement.TOKEN);
    }

    /** Whether the gateway must reject this request when no token is supplied. */
    public boolean requiresToken() {
        return authentication == AuthenticationRequirement.TOKEN;
    }

    private static void requireAbsolute(String name, String field, String path) {
        if (!path.startsWith("/")) {
            throw new IllegalArgumentException(
                    "resource '%s': %s must start with '/' but was '%s'"
                            .formatted(name, field, path));
        }
    }

    /**
     * Both templates must declare exactly the same variable names, otherwise a request could not be
     * forwarded: a variable present on one side only has nowhere to come from, or nowhere to go.
     */
    private static void requireMatchingVariables(
            String name, String exposedPath, String legacyPath) {
        Set<String> exposed = uniqueVariables(name, "exposedPath", exposedPath);
        Set<String> legacy = uniqueVariables(name, "legacyPath", legacyPath);
        if (!exposed.equals(legacy)) {
            throw new IllegalArgumentException(
                    "resource '%s': path variables differ — exposed %s vs legacy %s"
                            .formatted(name, exposed, legacy));
        }
    }

    private static Set<String> uniqueVariables(String name, String field, String path) {
        List<String> names = PathTemplate.variableNames(path);
        Set<String> unique = new HashSet<>(names);
        if (unique.size() != names.size()) {
            throw new IllegalArgumentException(
                    "resource '%s': %s declares a variable more than once: '%s'"
                            .formatted(name, field, path));
        }
        return unique;
    }

    /**
     * Neither the header names nor the query parameters they come from may repeat: a duplicate
     * would mean two definitions racing to fill the same slot.
     */
    private static void requireDistinctHeaders(String name, List<LegacyHeaderMapping> headers) {
        Set<String> legacyNames = new HashSet<>();
        Set<String> queryNames = new HashSet<>();
        for (LegacyHeaderMapping header : headers) {
            if (!legacyNames.add(header.legacyName().toLowerCase(java.util.Locale.ROOT))) {
                throw new IllegalArgumentException(
                        "resource '%s': legacy header '%s' is declared twice"
                                .formatted(name, header.legacyName()));
            }
            if (!queryNames.add(header.fromQuery())) {
                throw new IllegalArgumentException(
                        "resource '%s': query parameter '%s' feeds more than one header"
                                .formatted(name, header.fromQuery()));
            }
        }
    }

    /** Variable names of the exposed template, in declaration order. */
    public List<String> pathVariables() {
        return PathTemplate.variableNames(exposedPath);
    }
}
