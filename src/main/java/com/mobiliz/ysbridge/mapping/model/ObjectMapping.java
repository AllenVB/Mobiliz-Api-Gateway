package com.mobiliz.ysbridge.mapping.model;

import com.mobiliz.ysbridge.mapping.UnmappedPolicy;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Mapping for one JSON object: its field mappings plus the policy for fields that have none.
 *
 * <p>Both lookup directions are indexed here, and the constructor rejects any definition that could
 * not be translated back unambiguously. A mapping that survives construction is reversible by
 * definition.
 */
public final class ObjectMapping {

    private final List<FieldMapping> fields;
    private final UnmappedPolicy unmapped;
    private final Map<String, FieldMapping> byLegacyName;
    private final Map<List<String>, FieldMapping> byExposedPath;

    public ObjectMapping(List<FieldMapping> fields, UnmappedPolicy unmapped) {
        this.fields = List.copyOf(Objects.requireNonNull(fields, "fields"));
        this.unmapped = Objects.requireNonNull(unmapped, "unmapped");

        Map<String, FieldMapping> legacyIndex = new LinkedHashMap<>();
        Map<List<String>, FieldMapping> exposedIndex = new LinkedHashMap<>();
        for (FieldMapping field : this.fields) {
            FieldMapping duplicateLegacy = legacyIndex.put(field.legacyName(), field);
            if (duplicateLegacy != null) {
                throw new IllegalArgumentException(
                        "legacy field '%s' is mapped twice".formatted(field.legacyName()));
            }
            FieldMapping duplicateExposed = exposedIndex.put(field.exposedPath(), field);
            if (duplicateExposed != null) {
                throw new IllegalArgumentException(
                        "legacy fields '%s' and '%s' both map to exposed path '%s'"
                                .formatted(
                                        duplicateExposed.legacyName(),
                                        field.legacyName(),
                                        field.exposedPathAsString()));
            }
        }
        rejectPathsNestedUnderValues(exposedIndex);

        this.byLegacyName = Map.copyOf(legacyIndex);
        this.byExposedPath = Map.copyOf(exposedIndex);
    }

    /**
     * Rejects a definition where one exposed path is a prefix of another, such as {@code
     * appVersions} holding a value while {@code appVersions.android} also exists. That shape cannot
     * be represented in JSON.
     */
    private static void rejectPathsNestedUnderValues(Map<List<String>, FieldMapping> exposedIndex) {
        for (Map.Entry<List<String>, FieldMapping> entry : exposedIndex.entrySet()) {
            List<String> path = entry.getKey();
            for (int end = 1; end < path.size(); end++) {
                FieldMapping conflict = exposedIndex.get(path.subList(0, end));
                if (conflict != null) {
                    throw new IllegalArgumentException(
                            "exposed path '%s' is nested under '%s', which already holds a value"
                                    .formatted(
                                            entry.getValue().exposedPathAsString(),
                                            conflict.exposedPathAsString()));
                }
            }
        }
    }

    public static ObjectMapping of(UnmappedPolicy unmapped, FieldMapping... fields) {
        return new ObjectMapping(List.of(fields), unmapped);
    }

    public List<FieldMapping> fields() {
        return fields;
    }

    public UnmappedPolicy unmapped() {
        return unmapped;
    }

    public Optional<FieldMapping> byLegacyName(String legacyName) {
        return Optional.ofNullable(byLegacyName.get(legacyName));
    }

    public Optional<FieldMapping> byExposedPath(List<String> exposedPath) {
        return Optional.ofNullable(byExposedPath.get(exposedPath));
    }

    @Override
    public String toString() {
        return "ObjectMapping[fields=%d, unmapped=%s]".formatted(fields.size(), unmapped);
    }
}
