package com.mobiliz.ysbridge.mapping.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mobiliz.ysbridge.mapping.Direction;
import com.mobiliz.ysbridge.mapping.UnmappedPolicy;
import com.mobiliz.ysbridge.mapping.model.FieldMapping;
import com.mobiliz.ysbridge.mapping.model.FieldType;
import com.mobiliz.ysbridge.mapping.model.ObjectMapping;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Translates JSON payloads between the legacy shape and the exposed shape.
 *
 * <p>One {@link ObjectMapping} serves both directions: the model guarantees the mapping is
 * reversible, so {@code TO_LEGACY} is the exact inverse of {@code TO_ENGLISH}.
 *
 * <p><strong>Type coercion.</strong> {@link FieldType} declares the type of the <em>exposed</em>
 * field. Translating back renders the value in its canonical string form, which matches the case
 * this exists for: a legacy API that quotes its numbers. Declare a type only for fields the legacy
 * side sends as strings.
 *
 * <p><strong>Known boundaries</strong> (see {@code docs/open-items.md}):
 *
 * <ul>
 *   <li>{@code DECIMAL} yields a {@code DecimalNode}, which preserves precision but does not
 *       compare equal to a payload Jackson parsed with default settings.
 *   <li>Under {@code PASSTHROUGH_AND_REPORT}, an unmapped field nested deeper than one level in the
 *       exposed payload is reported and dropped: it lives inside a grouping object this bridge
 *       invented, so there is no legacy name to write it under.
 *   <li>A value outside its table, or one that will not coerce, is reported and passed through
 *       rather than rejected, so a new legacy value cannot take the bridge down.
 * </ul>
 *
 * <p>Nothing here mutates the input; a fresh tree is always returned.
 */
public final class JsonTranslator {

    private final JsonNodeFactory nodes = JsonNodeFactory.instance;

    /**
     * Translates {@code source}, which may be an object, an array of objects, or null.
     *
     * @throws TranslationException if the payload does not fit the mapping
     * @throws UnmappedFieldException if an unmapped field is found under {@code FAIL}
     */
    public JsonNode translate(
            JsonNode source,
            ObjectMapping mapping,
            Direction direction,
            TranslationObserver observer) {
        if (source == null || source.isNull() || source.isMissingNode()) {
            return NullNode.getInstance();
        }
        return translateNode(source, mapping, direction, observer, "");
    }

    private JsonNode translateNode(
            JsonNode source,
            ObjectMapping mapping,
            Direction direction,
            TranslationObserver observer,
            String path) {
        if (source.isNull() || source.isMissingNode()) {
            return NullNode.getInstance();
        }
        if (source.isArray()) {
            ArrayNode translated = nodes.arrayNode(source.size());
            int index = 0;
            for (JsonNode element : source) {
                translated.add(
                        translateNode(
                                element,
                                mapping,
                                direction,
                                observer,
                                "%s[%d]".formatted(path, index)));
                index++;
            }
            return translated;
        }
        if (!source.isObject()) {
            // A scalar carries no field names, so there is nothing to translate. Passing it
            // through keeps an upstream shape change from taking the bridge down; the
            // observer makes it visible instead.
            observer.unexpectedShape(direction, describe(path), source.getNodeType().toString());
            return source.deepCopy();
        }
        return direction == Direction.TO_ENGLISH
                ? toEnglish(source, mapping, observer, path)
                : toLegacy(source, mapping, observer, path);
    }

    // ---------------------------------------------------------------- to English

    private ObjectNode toEnglish(
            JsonNode source, ObjectMapping mapping, TranslationObserver observer, String path) {
        ObjectNode target = nodes.objectNode();
        for (Map.Entry<String, JsonNode> property : source.properties()) {
            String legacyName = property.getKey();
            JsonNode value = property.getValue();
            String childPath = join(path, legacyName);

            Optional<FieldMapping> found = mapping.byLegacyName(legacyName);
            if (found.isEmpty()) {
                switch (mapping.unmapped()) {
                    case FAIL -> throw new UnmappedFieldException(childPath);
                    case DROP -> {
                        /* deliberately omitted */
                    }
                    case PASSTHROUGH_AND_REPORT -> {
                        observer.unmappedField(Direction.TO_ENGLISH, childPath);
                        target.set(legacyName, value.deepCopy());
                    }
                }
                continue;
            }
            FieldMapping field = found.get();
            JsonNode translated =
                    translateValue(value, field, Direction.TO_ENGLISH, observer, childPath);
            writeAt(target, field.exposedPath(), translated);
        }
        return target;
    }

    /** Writes {@code value} at {@code path}, creating intermediate objects as needed. */
    private void writeAt(ObjectNode root, List<String> path, JsonNode value) {
        ObjectNode current = root;
        for (int i = 0; i < path.size() - 1; i++) {
            String segment = path.get(i);
            JsonNode existing = current.get(segment);
            if (existing instanceof ObjectNode object) {
                current = object;
            } else {
                ObjectNode created = nodes.objectNode();
                current.set(segment, created);
                current = created;
            }
        }
        current.set(path.getLast(), value);
    }

    // ----------------------------------------------------------------- to legacy

    private ObjectNode toLegacy(
            JsonNode source, ObjectMapping mapping, TranslationObserver observer, String path) {
        ObjectNode target = nodes.objectNode();

        for (FieldMapping field : mapping.fields()) {
            JsonNode value = readAt(source, field.exposedPath());
            if (value == null) {
                continue;
            }
            String childPath = join(path, field.exposedPathAsString());
            target.set(
                    field.legacyName(),
                    translateValue(value, field, Direction.TO_LEGACY, observer, childPath));
        }

        Set<List<String>> mappedPaths = new HashSet<>();
        for (FieldMapping field : mapping.fields()) {
            mappedPaths.add(field.exposedPath());
        }
        reportUnmapped(source, List.of(), mappedPaths, mapping.unmapped(), target, observer, path);
        return target;
    }

    /**
     * Walks the exposed payload looking for anything the mapping does not consume.
     *
     * <p>A path that is a prefix of a mapped path is a grouping object, so it is descended into
     * rather than reported.
     */
    private void reportUnmapped(
            JsonNode node,
            List<String> currentPath,
            Set<List<String>> mappedPaths,
            UnmappedPolicy policy,
            ObjectNode target,
            TranslationObserver observer,
            String pathPrefix) {
        if (!node.isObject()) {
            return;
        }
        for (Map.Entry<String, JsonNode> property : node.properties()) {
            List<String> childPath = append(currentPath, property.getKey());
            if (mappedPaths.contains(childPath)) {
                continue;
            }
            if (isPrefixOfAny(childPath, mappedPaths)) {
                reportUnmapped(
                        property.getValue(),
                        childPath,
                        mappedPaths,
                        policy,
                        target,
                        observer,
                        pathPrefix);
                continue;
            }
            String reportedPath = join(pathPrefix, String.join(".", childPath));
            switch (policy) {
                case FAIL -> throw new UnmappedFieldException(reportedPath);
                case DROP -> {
                    /* deliberately omitted */
                }
                case PASSTHROUGH_AND_REPORT -> {
                    observer.unmappedField(Direction.TO_LEGACY, reportedPath);
                    if (childPath.size() == 1) {
                        target.set(property.getKey(), property.getValue().deepCopy());
                    }
                    // Deeper than one level there is no legacy name to write it under,
                    // so the field is reported and dropped.
                }
            }
        }
    }

    private static boolean isPrefixOfAny(List<String> candidate, Set<List<String>> paths) {
        for (List<String> path : paths) {
            if (path.size() > candidate.size()
                    && path.subList(0, candidate.size()).equals(candidate)) {
                return true;
            }
        }
        return false;
    }

    /** Returns the node at {@code path}, or {@code null} when any segment is absent. */
    private static JsonNode readAt(JsonNode root, List<String> path) {
        JsonNode current = root;
        for (String segment : path) {
            if (current == null || !current.isObject()) {
                return null;
            }
            current = current.get(segment);
        }
        return current;
    }

    // -------------------------------------------------------------- value layer

    private JsonNode translateValue(
            JsonNode value,
            FieldMapping field,
            Direction direction,
            TranslationObserver observer,
            String path) {
        if (field.nested() != null) {
            return translateNode(value, field.nested(), direction, observer, path);
        }
        if (direction == Direction.TO_ENGLISH) {
            JsonNode result = emptyStringToNull(value, field);
            result = applyValueTable(result, field, direction, observer, path);
            return coerceToDeclaredType(result, field, observer, path);
        }
        JsonNode result = applyValueTable(value, field, direction, observer, path);
        result = coerceToLegacyString(result, field);
        return nullToEmptyString(result, field);
    }

    private JsonNode emptyStringToNull(JsonNode value, FieldMapping field) {
        if (field.emptyStringAsNull() && value.isTextual() && value.asText().isEmpty()) {
            return NullNode.getInstance();
        }
        return value;
    }

    private JsonNode nullToEmptyString(JsonNode value, FieldMapping field) {
        if (field.emptyStringAsNull() && value.isNull()) {
            return nodes.textNode("");
        }
        return value;
    }

    private JsonNode applyValueTable(
            JsonNode value,
            FieldMapping field,
            Direction direction,
            TranslationObserver observer,
            String path) {
        if (field.values() == null || value.isNull() || !value.isValueNode()) {
            return value;
        }
        String raw = value.asText();
        return field.values()
                .translate(raw, direction)
                .<JsonNode>map(nodes::textNode)
                .orElseGet(
                        () -> {
                            observer.unmappedValue(direction, path, raw);
                            return value;
                        });
    }

    private JsonNode coerceToDeclaredType(
            JsonNode value, FieldMapping field, TranslationObserver observer, String path) {
        FieldType type = field.type();
        if (type == null || value.isNull()) {
            return value;
        }
        String raw = value.asText();
        try {
            return switch (type) {
                case STRING -> value.isTextual() ? value : nodes.textNode(raw);
                case INTEGER -> value.isIntegralNumber() ? value : integerNode(raw.trim());
                case DECIMAL ->
                        value.isNumber() ? value : nodes.numberNode(new BigDecimal(raw.trim()));
                case BOOLEAN -> value.isBoolean() ? value : nodes.booleanNode(parseBoolean(raw));
            };
        } catch (IllegalArgumentException e) {
            observer.typeMismatch(Direction.TO_ENGLISH, path, raw, type);
            return value;
        }
    }

    /**
     * Produces the same node type Jackson would produce for the equivalent JSON literal: an int
     * where the value fits, a long otherwise. Emitting a long unconditionally would serialise
     * identically but compare unequal to a parsed payload, which would quietly weaken every
     * round-trip assertion.
     */
    private JsonNode integerNode(String raw) {
        long parsed = Long.parseLong(raw);
        return parsed >= Integer.MIN_VALUE && parsed <= Integer.MAX_VALUE
                ? nodes.numberNode((int) parsed)
                : nodes.numberNode(parsed);
    }

    private static boolean parseBoolean(String raw) {
        String normalised = raw.trim().toLowerCase(java.util.Locale.ROOT);
        return switch (normalised) {
            case "true", "1" -> true;
            case "false", "0" -> false;
            default -> throw new IllegalArgumentException("not a boolean: " + raw);
        };
    }

    private JsonNode coerceToLegacyString(JsonNode value, FieldMapping field) {
        if (field.type() == null || value.isNull() || value.isTextual()) {
            return value;
        }
        return nodes.textNode(value.asText());
    }

    // ------------------------------------------------------------------- paths

    private static String join(String prefix, String name) {
        return prefix.isEmpty() ? name : prefix + "." + name;
    }

    private static List<String> append(List<String> path, String segment) {
        List<String> extended = new ArrayList<>(path.size() + 1);
        extended.addAll(path);
        extended.add(segment);
        return List.copyOf(extended);
    }

    private static String describe(String path) {
        return path.isEmpty() ? "<root>" : path;
    }
}
