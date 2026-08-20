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

    public int offset() {
        return number * size;
    }
}
