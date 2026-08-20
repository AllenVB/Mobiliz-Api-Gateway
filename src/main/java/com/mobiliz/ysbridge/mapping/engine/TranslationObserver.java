package com.mobiliz.ysbridge.mapping.engine;

import com.mobiliz.ysbridge.mapping.Direction;
import com.mobiliz.ysbridge.mapping.model.FieldType;

/**
 * Receives everything the translator could not fully handle.
 *
 * <p>This is how gaps stay visible. A field the mapping does not know about, a value outside its
 * declared table, or a value that would not coerce are all reported here rather than failing the
 * request, so the mapping can be completed from evidence instead of guesswork.
 */
public interface TranslationObserver {

    /** A field with no mapping entry was encountered. */
    void unmappedField(Direction direction, String path);

    /** A value was outside the field's declared value table and was passed through unchanged. */
    void unmappedValue(Direction direction, String path, String value);

    /** A value did not match its declared type and was passed through unchanged. */
    void typeMismatch(Direction direction, String path, String value, FieldType expected);

    /**
     * A mapping expected an object or an array but the payload held a scalar, so there were no
     * names to translate and the value was passed through unchanged.
     *
     * <p>Seen in the wild: {@code DVRSonDurum} answers with a bare URL string as its whole result.
     * Reporting rather than failing keeps a shape change from taking the bridge down while still
     * making it visible.
     */
    void unexpectedShape(Direction direction, String path, String actualType);

    /** Observer that discards everything; useful in unit tests that assert only on output. */
    static TranslationObserver noop() {
        return new TranslationObserver() {

            @Override
            public void unmappedField(Direction direction, String path) {
                // intentionally ignored
            }

            @Override
            public void unmappedValue(Direction direction, String path, String value) {
                // intentionally ignored
            }

            @Override
            public void typeMismatch(
                    Direction direction, String path, String value, FieldType expected) {
                // intentionally ignored
            }

            @Override
            public void unexpectedShape(Direction direction, String path, String actualType) {
                // intentionally ignored
            }
        };
    }
}
