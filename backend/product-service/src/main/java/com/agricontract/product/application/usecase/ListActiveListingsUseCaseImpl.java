package com.agricontract.product.application.usecase;

import com.agricontract.product.application.dto.ListingResponse;
import com.agricontract.product.application.dto.ListingSearchItem;
import com.agricontract.product.application.dto.ListingSearchQuery;
import com.agricontract.product.application.dto.ListingSearchResult;
import com.agricontract.product.application.dto.PagedResult;
import com.agricontract.product.application.dto.ReputationLookup;
import com.agricontract.product.application.dto.ReputationStatus;
import com.agricontract.product.application.dto.SellerReputation;
import com.agricontract.product.application.port.SellerReputationPort;
import com.agricontract.product.common.exception.ReputationSignInRequiredException;
import com.agricontract.product.domain.model.Listing;
import com.agricontract.product.domain.model.vo.ListingStatus;
import com.agricontract.product.domain.repository.ListingRepository;
import com.agricontract.product.domain.repository.PageQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Active listing search with seller reputation from the reputation-service public summary (reputation design §6.2).
 * The filter runs on the page read from the database. A seller whose summary could not be read is kept, not
 * dropped: an outage degrades the filter, it never fails or empties the search.
 */
@Service
@RequiredArgsConstructor
public class ListActiveListingsUseCaseImpl implements ListActiveListingsUseCase {
    private final ListingRepository listingRepository;
    private final SellerReputationPort sellerReputationPort;

    @Override
    public ListingSearchResult execute(ListingSearchQuery query) {
        if (query.viewer() == null && query.minSellerScore() != null) {
            throw new ReputationSignInRequiredException();
        }
        PageQuery page = query.page();
        List<Listing> listings = listingRepository.findByStatus(ListingStatus.ACTIVE, page);
        long total = listingRepository.countByStatus(ListingStatus.ACTIVE);
        int totalPages = (int) Math.ceil((double) total / page.size());

        if (query.viewer() == null) {
            List<ListingSearchItem> items = listings.stream()
                    .map(listing -> new ListingSearchItem(ListingResponse.from(listing), null)).toList();
            return new ListingSearchResult(new PagedResult<>(items, total, totalPages),
                    ReputationStatus.SIGN_IN_REQUIRED);
        }

        Set<String> sellerIds = new LinkedHashSet<>();
        listings.forEach(listing -> sellerIds.add(listing.getSellerId()));
        ReputationLookup lookup = sellerIds.isEmpty()
                ? new ReputationLookup(Map.of(), true)
                : sellerReputationPort.lookup(sellerIds);

        List<ListingSearchItem> items = listings.stream()
                .map(listing -> new ListingSearchItem(ListingResponse.from(listing),
                        lookup.bySeller().get(listing.getSellerId())))
                .filter(item -> passes(item.sellerReputation(), query.minSellerScore()))
                .toList();
        ReputationStatus status = lookup.complete() ? ReputationStatus.AVAILABLE : ReputationStatus.UNAVAILABLE;
        return new ListingSearchResult(new PagedResult<>(items, total, totalPages), status);
    }

    // Unknown (read failed) and unrated (no history yet) sellers pass: a new seller is not penalized for being new.
    private static boolean passes(SellerReputation reputation, Integer minSellerScore) {
        return minSellerScore == null || reputation == null || reputation.score() == null
                || reputation.score() >= minSellerScore;
    }
}
