package com.mobiliz.ysbridge.mapping.engine;

import com.mobiliz.ysbridge.mapping.Direction;
import com.mobiliz.ysbridge.mapping.model.FieldType;
import java.util.ArrayList;
import java.util.List;

/** Test observer that records what the translator could not fully handle. */
final class RecordingObserver implements TranslationObserver {

    private final List<String> unmappedFields = new ArrayList<>();
    private final List<String> unmappedValues = new ArrayList<>();
    private final List<String> typeMismatches = new ArrayList<>();
    private final List<String> unexpectedShapes = new ArrayList<>();

    @Override
    public void unmappedField(Direction direction, String path) {
        unmappedFields.add(direction + " " + path);
    }

    @Override
    public void unmappedValue(Direction direction, String path, String value) {
        unmappedValues.add(direction + " " + path + "=" + value);
    }

    @Override
    public void typeMismatch(Direction direction, String path, String value, FieldType expected) {
        typeMismatches.add(direction + " " + path + "=" + value + " expected " + expected);
    }

    @Override
    public void unexpectedShape(Direction direction, String path, String actualType) {
        unexpectedShapes.add(direction + " " + path + " is " + actualType);
    }

    List<String> unexpectedShapes() {
        return unexpectedShapes;
    }

    List<String> unmappedFields() {
        return unmappedFields;
    }

    List<String> unmappedValues() {
        return unmappedValues;
    }

    List<String> typeMismatches() {
        return typeMismatches;
    }
}
