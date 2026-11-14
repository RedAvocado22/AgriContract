package com.agricontract.product.domain.repository;

/**
 * One page of a repository read.
 *
 * @param page       zero-based page number
 * @param sortBy     property to sort by; null = repository default order
 * @param descending sort direction, ignored when {@code sortBy} is null
 */
public record PageQuery(int page, int size, String sortBy, boolean descending) {
    public PageQuery {
        if (page < 0) {
            throw new IllegalArgumentException("page must not be negative");
        }
        if (size < 1) {
            throw new IllegalArgumentException("size must be at least 1");
        }
    }

    public static PageQuery of(int page, int size) {
        return new PageQuery(page, size, null, false);
    }
}
