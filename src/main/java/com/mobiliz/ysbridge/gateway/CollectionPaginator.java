package com.mobiliz.ysbridge.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mobiliz.ysbridge.config.BridgeProperties;
import com.mobiliz.ysbridge.gateway.exception.InvalidPageRequestException;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Slices collection responses, and wraps them so the shape can grow.
 *
 * <p>Two decisions are worth stating.
 *
 * <p><strong>A collection is never returned as a bare JSON array.</strong> An array has no room for
 * anything but its elements: page counts, warnings, deprecation notices could never be added
 * without breaking every caller. Wrapping in an object costs one level of nesting once, and buys
 * the ability to add fields forever.
 *
 * <p><strong>The page metadata carries only what cannot be derived.</strong> {@code first}, {@code
 * last} and {@code hasNext} are each one comparison away from {@code number} and {@code
 * totalPages}, so they are left out rather than repeated. {@code totalPages} stays, because
 * computing it means a ceiling division whose zero-element case is a classic off-by-one — the sort
 * of thing worth doing once here instead of wrongly in every client.
 */
public class CollectionPaginator {

    public static final String PAGE_PARAMETER = "page";
    public static final String SIZE_PARAMETER = "size";

    private final JsonNodeFactory nodes = JsonNodeFactory.instance;
    private final int defaultSize;
    private final int maxSize;

    public CollectionPaginator(BridgeProperties properties) {
        this.defaultSize = properties.pagination().defaultSize();
        this.maxSize = properties.pagination().maxSize();
    }

    /**
     * @throws InvalidPageRequestException if either parameter is not a usable number
     */
    public PageRequest parse(HttpServletRequest request) {
        int number = readInt(request, PAGE_PARAMETER, 0);
        int size = readInt(request, SIZE_PARAMETER, defaultSize);

        if (number < 0) {
            throw new InvalidPageRequestException(
                    PAGE_PARAMETER, "must not be negative, but was " + number);
        }
        if (size < 1) {
            throw new InvalidPageRequestException(
                    SIZE_PARAMETER, "must be at least 1, but was " + size);
        }
        if (size > maxSize) {
            throw new InvalidPageRequestException(
                    SIZE_PARAMETER, "must not exceed %d, but was %d".formatted(maxSize, size));
        }
        return new PageRequest(number, size);
    }

    private static int readInt(HttpServletRequest request, String parameter, int fallback) {
        String raw = request.getParameter(parameter);
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            throw new InvalidPageRequestException(
                    parameter, "must be a whole number, but was '%s'".formatted(raw));
        }
    }

    /**
     * Returns the requested slice of {@code array}, or an empty array when it lies past the end.
     */
    public ArrayNode slice(JsonNode array, PageRequest page) {
        ArrayNode slice = nodes.arrayNode();
        // The cast is safe because the minimum is taken against the array's own size first.
        int from = (int) Math.min(page.offset(), array.size());
        int to = (int) Math.min((long) from + page.size(), array.size());
        for (int index = from; index < to; index++) {
            slice.add(array.get(index));
        }
        return slice;
    }

    /**
     * @param items the translated page contents
     * @param page the page that was requested
     * @param totalElements size of the whole collection, before slicing
     */
    public ObjectNode wrap(JsonNode items, PageRequest page, int totalElements) {
        ObjectNode body = nodes.objectNode();
        body.set("items", items);

        ObjectNode metadata = body.putObject("page");
        metadata.put("number", page.number());
        metadata.put("size", page.size());
        metadata.put("totalElements", totalElements);
        metadata.put("totalPages", totalPages(totalElements, page.size()));
        return body;
    }

    /** Ceiling division, with the empty collection reported as zero pages rather than one. */
    private static int totalPages(int totalElements, int size) {
        return totalElements == 0 ? 0 : (totalElements + size - 1) / size;
    }
}
