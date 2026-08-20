package com.mobiliz.ysbridge.mapping.model;

import com.mobiliz.ysbridge.mapping.Direction;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Bidirectional value table for a single field, used for enum-like values such as {@code AKTIF ->
 * ACTIVE}.
 *
 * <p>The reverse table is derived at construction time, which is also where an ambiguous mapping is
 * rejected: if two legacy values mapped to the same exposed value, translating back would have to
 * guess.
 */
public final class ValueMapping {

    private final Map<String, String> legacyToExposed;
    private final Map<String, String> exposedToLegacy;

    private ValueMapping(Map<String, String> legacyToExposed, Map<String, String> exposedToLegacy) {
        this.legacyToExposed = Map.copyOf(legacyToExposed);
        this.exposedToLegacy = Map.copyOf(exposedToLegacy);
    }

    /**
     * @param legacyToExposed legacy value to exposed value; must not be empty
     * @throws IllegalArgumentException if the table is empty or not reversible
     */
    public static ValueMapping of(Map<String, String> legacyToExposed) {
        if (legacyToExposed == null || legacyToExposed.isEmpty()) {
            throw new IllegalArgumentException("value mapping must declare at least one value");
        }
        Map<String, String> reverse = new LinkedHashMap<>();
        legacyToExposed.forEach(
                (legacy, exposed) -> {
                    if (legacy == null
                            || legacy.isBlank()
                            || exposed == null
                            || exposed.isBlank()) {
                        throw new IllegalArgumentException(
                                "value mapping must not contain blank entries");
                    }
                    String previous = reverse.put(exposed, legacy);
                    if (previous != null) {
                        throw new IllegalArgumentException(
                                "ambiguous reverse value mapping: legacy values '%s' and '%s' both map to '%s'"
                                        .formatted(previous, legacy, exposed));
                    }
                });
        return new ValueMapping(legacyToExposed, reverse);
    }

    /** Returns the counterpart of {@code value}, or empty when the table has no entry for it. */
    public Optional<String> translate(String value, Direction direction) {
        Map<String, String> table =
                direction == Direction.TO_ENGLISH ? legacyToExposed : exposedToLegacy;
        return Optional.ofNullable(table.get(value));
    }

    public Map<String, String> legacyToExposed() {
        return legacyToExposed;
    }

    @Override
    public String toString() {
        return "ValueMapping" + legacyToExposed;
    }
}
