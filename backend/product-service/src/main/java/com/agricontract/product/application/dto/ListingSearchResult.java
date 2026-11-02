package com.agricontract.product.application.dto;

/**
 * {@code page.totalElements} counts ACTIVE listings before the reputation filter: the filter runs on the page that
 * was read, so a filtered page can hold fewer than {@code size} items.
 */
public record ListingSearchResult(PagedResult<ListingSearchItem> page, ReputationStatus reputationStatus) {
}
