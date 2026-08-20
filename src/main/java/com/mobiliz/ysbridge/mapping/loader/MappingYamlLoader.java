package com.mobiliz.ysbridge.mapping.loader;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.mobiliz.ysbridge.mapping.UnmappedPolicy;
import com.mobiliz.ysbridge.mapping.model.AuthenticationRequirement;
import com.mobiliz.ysbridge.mapping.model.FieldMapping;
import com.mobiliz.ysbridge.mapping.model.FieldType;
import com.mobiliz.ysbridge.mapping.model.LegacyHeaderMapping;
import com.mobiliz.ysbridge.mapping.model.ObjectMapping;
import com.mobiliz.ysbridge.mapping.model.ResourceMapping;
import com.mobiliz.ysbridge.mapping.model.ValueMapping;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.http.HttpMethod;

/**
 * Compiles YAML mapping documents into {@link ResourceMapping} instances.
 *
 * <p>The loader is deliberately strict: an unknown key is an error rather than a silently ignored
 * line. A typo in a mapping file would otherwise surface much later, as a field quietly missing
 * from a production response.
 */
public final class MappingYamlLoader {

    private static final Set<String> ROOT_KEYS = Set.of("resources");
    private static final Set<String> RESOURCE_KEYS =
            Set.of(
                    "method",
                    "exposedPath",
                    "legacyPath",
                    "request",
                    "response",
                    "legacyHeaders",
                    "authentication");
    private static final Set<String> HEADER_KEYS = Set.of("fromQuery", "required");
    private static final Set<String> OBJECT_KEYS = Set.of("unmapped", "fields");
    private static final Set<String> FIELD_KEYS =
            Set.of("exposed", "values", "type", "emptyStringAsNull", "nested");

    private static final Set<String> SUPPORTED_METHODS =
            Set.of("GET", "POST", "PUT", "PATCH", "DELETE");

    private static final UnmappedPolicy DEFAULT_UNMAPPED = UnmappedPolicy.PASSTHROUGH_AND_REPORT;

    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

    /**
     * @param yaml document contents
     * @param source name used in error messages, typically the file name
     * @return the resources declared in the document, in declaration order
     * @throws MappingConfigurationException if the document is malformed
     */
    public List<ResourceMapping> parse(String yaml, String source) {
        JsonNode root;
        try {
            root = yamlMapper.readTree(yaml);
        } catch (JacksonException e) {
            throw new MappingConfigurationException(source, "is not valid YAML", e);
        }
        if (root == null || root.isNull() || root.isMissingNode()) {
            throw new MappingConfigurationException(source, "is empty");
        }
        requireObject(root, source);
        rejectUnknownKeys(root, ROOT_KEYS, source);

        JsonNode resources = root.get("resources");
        if (resources == null || resources.isNull()) {
            throw new MappingConfigurationException(source, "must declare 'resources'");
        }
        requireObject(resources, source + " -> resources");
        if (resources.isEmpty()) {
            throw new MappingConfigurationException(
                    source + " -> resources", "must declare at least one resource");
        }

        List<ResourceMapping> parsed = new ArrayList<>();
        for (Map.Entry<String, JsonNode> entry : resources.properties()) {
            String name = entry.getKey();
            parsed.add(readResource(name, entry.getValue(), source + " -> resources." + name));
        }
        return List.copyOf(parsed);
    }

    private ResourceMapping readResource(String name, JsonNode node, String location) {
        requireObject(node, location);
        rejectUnknownKeys(node, RESOURCE_KEYS, location);

        HttpMethod method = readMethod(node, location);
        String exposedPath = requireText(node, "exposedPath", location);
        String legacyPath = requireText(node, "legacyPath", location);
        ObjectMapping request = readOptionalObjectMapping(node, "request", location);
        ObjectMapping response = readOptionalObjectMapping(node, "response", location);
        List<LegacyHeaderMapping> legacyHeaders = readLegacyHeaders(node, location);

        AuthenticationRequirement authentication = AuthenticationRequirement.TOKEN;
        JsonNode authenticationNode = node.get("authentication");
        if (authenticationNode != null && !authenticationNode.isNull()) {
            authentication =
                    readEnum(
                            AuthenticationRequirement.class,
                            authenticationNode,
                            location + ".authentication");
        }

        try {
            return new ResourceMapping(
                    name,
                    method,
                    exposedPath,
                    legacyPath,
                    request,
                    response,
                    legacyHeaders,
                    authentication);
        } catch (IllegalArgumentException e) {
            throw new MappingConfigurationException(location, e.getMessage(), e);
        }
    }

    private List<LegacyHeaderMapping> readLegacyHeaders(JsonNode parent, String location) {
        JsonNode node = parent.get("legacyHeaders");
        if (node == null || node.isNull()) {
            return List.of();
        }
        String headersLocation = location + ".legacyHeaders";
        requireObject(node, headersLocation);

        List<LegacyHeaderMapping> headers = new ArrayList<>();
        for (Map.Entry<String, JsonNode> entry : node.properties()) {
            String legacyName = entry.getKey();
            String headerLocation = headersLocation + "." + legacyName;
            JsonNode definition = entry.getValue();

            String fromQuery;
            boolean required = false;
            if (definition.isTextual()) {
                fromQuery = definition.asText().trim();
            } else {
                requireObject(definition, headerLocation);
                rejectUnknownKeys(definition, HEADER_KEYS, headerLocation);
                fromQuery = requireText(definition, "fromQuery", headerLocation);
                JsonNode requiredNode = definition.get("required");
                if (requiredNode != null && !requiredNode.isNull()) {
                    if (!requiredNode.isBoolean()) {
                        throw new MappingConfigurationException(
                                headerLocation + ".required", "must be true or false");
                    }
                    required = requiredNode.booleanValue();
                }
            }
            try {
                headers.add(new LegacyHeaderMapping(legacyName, fromQuery, required));
            } catch (IllegalArgumentException e) {
                throw new MappingConfigurationException(headerLocation, e.getMessage(), e);
            }
        }
        return List.copyOf(headers);
    }

    private HttpMethod readMethod(JsonNode node, String location) {
        String raw = requireText(node, "method", location);
        String normalised = raw.toUpperCase(Locale.ROOT);
        if (!SUPPORTED_METHODS.contains(normalised)) {
            throw new MappingConfigurationException(
                    location + ".method",
                    "'%s' is not supported; use one of %s"
                            .formatted(raw, new TreeSet<>(SUPPORTED_METHODS)));
        }
        return HttpMethod.valueOf(normalised);
    }

    private ObjectMapping readOptionalObjectMapping(JsonNode parent, String key, String location) {
        JsonNode node = parent.get(key);
        if (node == null || node.isNull()) {
            return null;
        }
        return readObjectMapping(node, location + "." + key);
    }

    private ObjectMapping readObjectMapping(JsonNode node, String location) {
        requireObject(node, location);
        rejectUnknownKeys(node, OBJECT_KEYS, location);

        UnmappedPolicy unmapped = DEFAULT_UNMAPPED;
        JsonNode unmappedNode = node.get("unmapped");
        if (unmappedNode != null && !unmappedNode.isNull()) {
            unmapped = readEnum(UnmappedPolicy.class, unmappedNode, location + ".unmapped");
        }

        List<FieldMapping> fields = new ArrayList<>();
        JsonNode fieldsNode = node.get("fields");
        if (fieldsNode != null && !fieldsNode.isNull()) {
            requireObject(fieldsNode, location + ".fields");
            for (Map.Entry<String, JsonNode> entry : fieldsNode.properties()) {
                String legacyName = entry.getKey();
                fields.add(
                        readField(
                                legacyName, entry.getValue(), location + ".fields." + legacyName));
            }
        }

        try {
            return new ObjectMapping(fields, unmapped);
        } catch (IllegalArgumentException e) {
            throw new MappingConfigurationException(location, e.getMessage(), e);
        }
    }

    private FieldMapping readField(String legacyName, JsonNode node, String location) {
        if (node.isTextual()) {
            return buildField(legacyName, node.asText(), null, false, null, null, location);
        }
        requireObject(node, location);
        rejectUnknownKeys(node, FIELD_KEYS, location);

        String exposed = requireText(node, "exposed", location);
        FieldType type = null;
        JsonNode typeNode = node.get("type");
        if (typeNode != null && !typeNode.isNull()) {
            type = readEnum(FieldType.class, typeNode, location + ".type");
        }

        boolean emptyStringAsNull = false;
        JsonNode emptyNode = node.get("emptyStringAsNull");
        if (emptyNode != null && !emptyNode.isNull()) {
            if (!emptyNode.isBoolean()) {
                throw new MappingConfigurationException(
                        location + ".emptyStringAsNull", "must be true or false");
            }
            emptyStringAsNull = emptyNode.booleanValue();
        }

        ValueMapping values = null;
        JsonNode valuesNode = node.get("values");
        if (valuesNode != null && !valuesNode.isNull()) {
            values = readValues(valuesNode, location + ".values");
        }

        ObjectMapping nested = null;
        JsonNode nestedNode = node.get("nested");
        if (nestedNode != null && !nestedNode.isNull()) {
            nested = readObjectMapping(nestedNode, location + ".nested");
        }

        return buildField(legacyName, exposed, type, emptyStringAsNull, values, nested, location);
    }

    private FieldMapping buildField(
            String legacyName,
            String exposed,
            FieldType type,
            boolean emptyStringAsNull,
            ValueMapping values,
            ObjectMapping nested,
            String location) {
        List<String> exposedPath = splitExposedPath(exposed, location);
        try {
            return new FieldMapping(
                    legacyName, exposedPath, type, emptyStringAsNull, values, nested);
        } catch (IllegalArgumentException e) {
            throw new MappingConfigurationException(location, e.getMessage(), e);
        }
    }

    private static List<String> splitExposedPath(String exposed, String location) {
        if (exposed.isBlank()) {
            throw new MappingConfigurationException(location, "exposed name must not be blank");
        }
        List<String> segments = List.of(exposed.split("\\.", -1));
        for (String segment : segments) {
            if (segment.isBlank()) {
                throw new MappingConfigurationException(
                        location, "exposed path '%s' has an empty segment".formatted(exposed));
            }
        }
        return segments;
    }

    private ValueMapping readValues(JsonNode node, String location) {
        requireObject(node, location);
        Map<String, String> table = new LinkedHashMap<>();
        for (Map.Entry<String, JsonNode> entry : node.properties()) {
            JsonNode value = entry.getValue();
            if (value.isNull() || !value.isValueNode()) {
                throw new MappingConfigurationException(
                        location + "." + entry.getKey(), "must be a scalar value");
            }
            table.put(entry.getKey(), value.asText());
        }
        try {
            return ValueMapping.of(table);
        } catch (IllegalArgumentException e) {
            throw new MappingConfigurationException(location, e.getMessage(), e);
        }
    }

    private static <E extends Enum<E>> E readEnum(Class<E> type, JsonNode node, String location) {
        if (!node.isTextual()) {
            throw new MappingConfigurationException(location, "must be a string");
        }
        String raw = node.asText().trim().toUpperCase(Locale.ROOT);
        for (E candidate : type.getEnumConstants()) {
            if (candidate.name().equals(raw)) {
                return candidate;
            }
        }
        throw new MappingConfigurationException(
                location,
                "'%s' is not one of %s"
                        .formatted(node.asText(), Arrays.toString(type.getEnumConstants())));
    }

    private static void requireObject(JsonNode node, String location) {
        if (!node.isObject()) {
            throw new MappingConfigurationException(location, "must be a mapping (an object)");
        }
    }

    private static String requireText(JsonNode parent, String key, String location) {
        JsonNode node = parent.get(key);
        if (node == null || node.isNull()) {
            throw new MappingConfigurationException(location, "must declare '%s'".formatted(key));
        }
        if (!node.isTextual() || node.asText().isBlank()) {
            throw new MappingConfigurationException(
                    location + "." + key, "must be a non-empty string");
        }
        return node.asText().trim();
    }

    private static void rejectUnknownKeys(JsonNode node, Set<String> allowed, String location) {
        Set<String> unknown = new TreeSet<>();
        for (Map.Entry<String, JsonNode> entry : node.properties()) {
            if (!allowed.contains(entry.getKey())) {
                unknown.add(entry.getKey());
            }
        }
        if (!unknown.isEmpty()) {
            throw new MappingConfigurationException(
                    location,
                    "has unknown key(s) %s; allowed keys are %s"
                            .formatted(unknown, new TreeSet<>(allowed)));
        }
    }
}
