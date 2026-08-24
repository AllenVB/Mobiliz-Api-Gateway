package com.mobiliz.ysbridge.gateway;

/**
 * A slice of a collection, requested by the caller.
 *
 * @param number zero-based page index
 * @param size maximum number of items in the page
 */
public record PageRequest(int number, int size) {

    public PageRequest {
        if (number < 0) {
            throw new IllegalArgumentException("page number must not be negative");
        }
        if (size < 1) {
            throw new IllegalArgumentException("page size must be at least 1");
        }
    }

    /**
     * Index of the first item on this page.
     *
     * <p>Returns a {@code long} deliberately. Both operands are caller-supplied, and {@code number
     * * size} in {@code int} arithmetic wraps negative for large pages — which then reads before
     * the start of the collection and produces a page of nulls rather than an empty one.
     */
    public long offset() {
        return (long) number * size;
    }
}
