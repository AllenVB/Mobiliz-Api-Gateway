package com.mobiliz.ysbridge.mapping;

/** Direction a translation runs in. */
public enum Direction {

    /** Legacy payload (Turkish names) to the exposed contract (English names). */
    TO_ENGLISH,

    /** Exposed payload (English names) back to the legacy contract (Turkish names). */
    TO_LEGACY;

    public Direction inverse() {
        return this == TO_ENGLISH ? TO_LEGACY : TO_ENGLISH;
    }
}
