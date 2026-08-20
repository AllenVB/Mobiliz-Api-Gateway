package com.mobiliz.ysbridge.mapping.model;

/**
 * Target JSON type for a field whose legacy representation is wrong, such as a numeric identifier
 * the legacy API sends as a quoted string.
 */
public enum FieldType {
    STRING,
    INTEGER,
    DECIMAL,
    BOOLEAN
}
