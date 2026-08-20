package com.mobiliz.ysbridge.mapping.model;

import java.util.List;
import java.util.Objects;

/**
 * Mapping of one legacy field onto its place in the exposed payload.
 *
 * <p>{@code exposedPath} has more than one element when the field is grouped, so {@code
 * androidversion} can surface as {@code appVersions.authorizedServiceAndroid}.
 *
 * @param legacyName field name as the legacy API spells it; the mapping key
 * @param exposedPath path the field takes in the exposed payload, outermost first
 * @param type target JSON type, or {@code null} to keep the legacy type
 * @param emptyStringAsNull whether {@code ""} becomes {@code null} when translating outbound
 * @param values enum-like value table, or {@code null}
 * @param nested mapping applied to an object or to each element of an array, or {@code null}
 */
public record FieldMapping(
        String legacyName,
        List<String> exposedPath,
        FieldType type,
        boolean emptyStringAsNull,
        ValueMapping values,
        ObjectMapping nested) {

    public FieldMapping {
        Objects.requireNonNull(legacyName, "legacyName");
        Objects.requireNonNull(exposedPath, "exposedPath");
        if (legacyName.isBlank()) {
            throw new IllegalArgumentException("legacy field name must not be blank");
        }
        exposedPath = List.copyOf(exposedPath);
        if (exposedPath.isEmpty()) {
            throw new IllegalArgumentException(
                    "field '%s' must declare an exposed name".formatted(legacyName));
        }
        for (String segment : exposedPath) {
            if (segment == null || segment.isBlank()) {
                throw new IllegalArgumentException(
                        "field '%s' has a blank segment in its exposed path".formatted(legacyName));
            }
        }
        if (values != null && nested != null) {
            throw new IllegalArgumentException(
                    "field '%s' cannot declare both 'values' and 'nested'".formatted(legacyName));
        }
        if (type != null && nested != null) {
            throw new IllegalArgumentException(
                    "field '%s' cannot declare both 'type' and 'nested'".formatted(legacyName));
        }
    }

    /** Convenience factory for the common case: a plain rename with no extras. */
    public static FieldMapping rename(String legacyName, String exposedName) {
        return new FieldMapping(legacyName, List.of(exposedName), null, false, null, null);
    }

    /** Last segment of the exposed path, i.e. the name the value is finally written under. */
    public String exposedLeaf() {
        return exposedPath.getLast();
    }

    /** Whether this field is nested under one or more exposed objects. */
    public boolean isGrouped() {
        return exposedPath.size() > 1;
    }

    /** Dotted rendering of the exposed path, for log and error messages. */
    public String exposedPathAsString() {
        return String.join(".", exposedPath);
    }
}
