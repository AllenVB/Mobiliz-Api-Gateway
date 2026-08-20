package com.mobiliz.ysbridge.mapping.model;

import java.util.ArrayList;
import java.util.List;

/** Helpers for URI templates of the form {@code /api/v1/work-orders/{workOrderId}/lines}. */
public final class PathTemplate {

    private PathTemplate() {}

    /**
     * Returns the variable names of {@code template} in declaration order.
     *
     * @throws IllegalArgumentException if a brace is unbalanced or a variable is unnamed
     */
    public static List<String> variableNames(String template) {
        List<String> names = new ArrayList<>();
        int cursor = 0;
        while (cursor < template.length()) {
            int open = template.indexOf('{', cursor);
            if (open < 0) {
                break;
            }
            int close = template.indexOf('}', open);
            if (close < 0) {
                throw new IllegalArgumentException(
                        "unbalanced '{' in path template '%s'".formatted(template));
            }
            String name = template.substring(open + 1, close);
            if (name.isBlank() || name.indexOf('{') >= 0) {
                throw new IllegalArgumentException(
                        "malformed path variable in template '%s'".formatted(template));
            }
            names.add(name);
            cursor = close + 1;
        }
        if (template.indexOf('}', cursor) >= 0) {
            throw new IllegalArgumentException(
                    "unbalanced '}' in path template '%s'".formatted(template));
        }
        return List.copyOf(names);
    }
}
