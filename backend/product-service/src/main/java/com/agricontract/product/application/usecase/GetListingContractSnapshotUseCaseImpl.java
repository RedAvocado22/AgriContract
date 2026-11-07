package com.agricontract.product.application.usecase;

import com.agricontract.product.application.dto.ListingContractSnapshotResponse;
import com.agricontract.product.application.dto.ListingContractSnapshotResponse.OriginSnapshot;
import com.agricontract.product.application.dto.ListingContractSnapshotResponse.PlotRef;
import com.agricontract.product.common.exception.ListingNotFoundException;
import com.agricontract.product.common.exception.ReservationConflictException;
import com.agricontract.product.domain.model.Category;
import com.agricontract.product.domain.model.Listing;
import com.agricontract.product.domain.model.vo.ListingGoods;
import com.agricontract.product.domain.model.vo.ListingId;
import com.agricontract.product.domain.model.vo.ListingPlot;
import com.agricontract.product.domain.repository.CategoryRepository;
import com.agricontract.product.domain.repository.ListingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

import static com.agricontract.product.common.exception.ReservationConflictException.LISTING_STATE_CONFLICT;
import static com.agricontract.product.common.exception.ReservationConflictException.LISTING_VERSION_MISMATCH;

/**
 * Source of the contract goods snapshot, read by contract-service at create before it reserves (design §8b).
 * Built only from the copies the listing took at creation, plus the category name, so a later Product or registry
 * edit does not reach it. Status and quantity are left to the reserve call that follows with the same token, so a
 * sold-out listing still reports INSUFFICIENT_LISTING_QUANTITY rather than a state conflict.
 */
@Service
@RequiredArgsConstructor
public class GetListingContractSnapshotUseCaseImpl implements GetListingContractSnapshotUseCase {

    private final ListingRepository listingRepository;
    private final CategoryRepository categoryRepository;

    @Override
    public ListingContractSnapshotResponse execute(String listingId, String listingVersionToken) {
        if (listingVersionToken == null || listingVersionToken.isBlank()) {
            throw new IllegalArgumentException("listingVersionToken must not be blank");
        }
        Listing listing = listingRepository.findById(new ListingId(listingId))
                .orElseThrow(() -> new ListingNotFoundException(listingId));
        if (!listing.isAtVersion(listingVersionToken)) {
            throw new ReservationConflictException(LISTING_VERSION_MISMATCH,
                    "Listing " + listingId + " has changed; reload it and retry with the new version token");
        }
        if (!listing.canBackCoffeeContract()) {
            throw notSnapshottable(listingId);
        }
        ListingGoods goods = listing.getGoods();
        String categoryName = categoryRepository.findById(goods.categoryId())
                .map(Category::getName)
                .filter(name -> !name.isBlank())
                .orElseThrow(() -> notSnapshottable(listingId));

        return new ListingContractSnapshotResponse(
                goods.commodity(),
                listing.getListingId().value(),
                listing.getProductId().value(),
                goods.categoryId().value(),
                categoryName,
                listing.getProductName(),
                ListingContractSnapshotResponse.UNIT_OF_MEASURE,
                goods.cropYear(),
                goods.harvestSeason(),
                new OriginSnapshot(goods.regionText(), plotRefs(listing.getPlots())));
    }

    // Fixed order so two reads of the same version serialize, and so hash, the same.
    private static List<PlotRef> plotRefs(List<ListingPlot> plots) {
        return plots.stream()
                .sorted(Comparator.comparing(ListingPlot::householdLabel)
                        .thenComparing(plot -> plot.sourceRegistryEntryId().value()))
                .map(plot -> new PlotRef(
                        plot.sourceRegistryEntryId().value(),
                        plot.householdLabel(),
                        plot.geometry().type(),
                        plot.geometry().geometryHash(),
                        plot.verificationLevel()))
                .toList();
    }

    private static ReservationConflictException notSnapshottable(String listingId) {
        return new ReservationConflictException(LISTING_STATE_CONFLICT,
                "Listing " + listingId + " cannot back a coffee contract: the seller must republish it");
    }
}
