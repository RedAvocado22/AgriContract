package com.agricontract.contract.domain.repository;

/**
 * Which page of a list to read, without Spring Data (D44). {@code sortBy} is a field name or null for the store's
 * default order.
 */
public record PageQuery(int page, int size, String sortBy, boolean descending) {
    public PageQuery {
        if (page < 0 || size < 1) {
            throw new IllegalArgumentException("page must be >= 0 and size >= 1");
        }
    }

    public static PageQuery of(int page, int size) {
        return new PageQuery(page, size, null, false);
    }
}
