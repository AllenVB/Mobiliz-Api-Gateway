package com.mobiliz.ysbridge.error;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Compiles the error catalog document.
 *
 * <p>As strict as the mapping loader, and for the same reason: a typo here would mean a legacy
 * error silently falling through to the generic case, which is exactly the kind of quiet
 * degradation this bridge exists to prevent.
 */
public final class ErrorCatalogLoader {

    private static final Set<String> ROOT_KEYS = Set.of("errors");
    private static final Set<String> RULE_KEYS = Set.of("match", "status", "code", "title");

    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

    public ErrorCatalog parse(String yaml, String source) {
        JsonNode root;
        try {
            root = yamlMapper.readTree(yaml);
        } catch (JacksonException e) {
            throw new ErrorCatalogException(source + " — is not valid YAML", e);
        }
        if (root == null || root.isNull() || !root.isObject()) {
            throw new ErrorCatalogException(source + " — must be a mapping (an object)");
        }
        rejectUnknownKeys(root, ROOT_KEYS, source);

        JsonNode errors = root.get("errors");
        if (errors == null || errors.isNull()) {
            throw new ErrorCatalogException(source + " — must declare 'errors'");
        }
        if (!errors.isArray()) {
            throw new ErrorCatalogException(source + " -> errors — must be a list");
        }

        List<ErrorRule> rules = new ArrayList<>();
        int index = 0;
        for (JsonNode node : errors) {
            String location = "%s -> errors[%d]".formatted(source, index);
            if (!node.isObject()) {
                throw new ErrorCatalogException(location + " — must be a mapping (an object)");
            }
            rejectUnknownKeys(node, RULE_KEYS, location);
            rules.add(
                    new ErrorRule(
                            requireText(node, "match", location),
                            requireInt(node, "status", location),
                            requireText(node, "code", location),
                            requireText(node, "title", location)));
            index++;
        }
        try {
            return new ErrorCatalog(rules);
        } catch (IllegalArgumentException e) {
            throw new ErrorCatalogException(source + " — " + e.getMessage(), e);
        }
    }

    private static String requireText(JsonNode parent, String key, String location) {
        JsonNode node = parent.get(key);
        if (node == null || !node.isTextual() || node.asText().isBlank()) {
            throw new ErrorCatalogException(
                    "%s.%s — must be a non-empty string".formatted(location, key));
        }
        return node.asText().trim();
    }

    private static int requireInt(JsonNode parent, String key, String location) {
        JsonNode node = parent.get(key);
        if (node == null || !node.isIntegralNumber()) {
            throw new ErrorCatalogException(
                    "%s.%s — must be a whole number".formatted(location, key));
        }
        return node.intValue();
    }

    private static void rejectUnknownKeys(JsonNode node, Set<String> allowed, String location) {
        Set<String> unknown = new TreeSet<>();
        for (Map.Entry<String, JsonNode> entry : node.properties()) {
            if (!allowed.contains(entry.getKey())) {
                unknown.add(entry.getKey());
            }
        }
        if (!unknown.isEmpty()) {
            throw new ErrorCatalogException(
                    "%s — has unknown key(s) %s; allowed keys are %s"
                            .formatted(location, unknown, new TreeSet<>(allowed)));
        }
    }

    /** Raised when the error catalog document cannot be compiled. */
    public static class ErrorCatalogException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        public ErrorCatalogException(String message) {
            super(message);
        }

        public ErrorCatalogException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
