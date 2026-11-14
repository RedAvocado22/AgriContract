package com.agricontract.product.application.usecase;

import com.agricontract.product.application.dto.CreateListingRequest;
import com.agricontract.product.application.dto.CreatedListing;
import com.agricontract.product.application.dto.ListingResponse;
import com.agricontract.product.common.exception.IdempotencyKeyReusedException;
import com.agricontract.product.common.exception.ProductNotFoundException;
import com.agricontract.product.common.exception.WriteConflictException;
import com.agricontract.product.domain.model.Category;
import com.agricontract.product.domain.model.Listing;
import com.agricontract.product.domain.model.Product;
import com.agricontract.product.domain.model.vo.CategoryId;
import com.agricontract.product.domain.model.vo.ListingGoods;
import com.agricontract.product.domain.model.vo.ListingId;
import com.agricontract.product.domain.model.vo.ListingPlot;
import com.agricontract.product.domain.model.vo.ProductId;
import com.agricontract.product.domain.repository.CategoryRepository;
import com.agricontract.product.domain.repository.ListingRepository;
import com.agricontract.product.domain.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CreateListingUseCaseImpl implements CreateListingUseCase {

    private final ListingRepository listingRepository;
    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final ListingPlotPicker plotPicker;
    private final SellerTradingGate tradingGate;

    @Override
    public CreatedListing execute(String sellerId, String idempotencyKey, CreateListingRequest request) {
        ListingRequests.requireIdempotencyKey(idempotencyKey);
        tradingGate.assertEligible(sellerId);

        Optional<ListingResponse> replayed = replayOf(sellerId, idempotencyKey, request);
        if (replayed.isPresent()) {
            return new CreatedListing(replayed.get(), false);
        }

        Product product = productRepository.findById(new ProductId(request.productId()))
                .orElseThrow(() -> new ProductNotFoundException(request.productId()));
        Category category = coffeeCategoryOf(product);
        ListingRequests.requireSameProductName(product.getName(), request);
        List<ListingPlot> plots = plotPicker.pick(sellerId, request.registryEntryIds());

        Listing listing = Listing.create(
                new ListingId(UUID.randomUUID().toString()),
                sellerId,
                new ProductId(request.productId()),
                product.getName(),
                product.getCoverImageUrl(),
                request.offeredQuantity(),
                request.minOrderQuantity(),
                request.askingUnitPriceValue(),
                plots,
                ListingRequests.goods(new ListingGoods(
                        category.getCategoryId(),
                        category.getCommodity(),
                        product.getCropYear(),
                        product.getHarvestSeason(),
                        product.getCertifications(),
                        null, null, null, null, null, null), request)
        );

        Listing saved;
        try {
            saved = listingRepository.save(listing, idempotencyKey);
        } catch (WriteConflictException e) {
            // a concurrent request with the same key committed first
            return new CreatedListing(replayOf(sellerId, idempotencyKey, request).orElseThrow(() -> e), false);
        }

        return new CreatedListing(ListingResponse.from(saved), true);
    }

    /**
     * The listing this seller already created with this key: returned again when the body is the same, 409 when
     * the key is reused for a different body. The key is per seller, so another seller's listing never matches.
     */
    private Optional<ListingResponse> replayOf(String sellerId, String idempotencyKey, CreateListingRequest request) {
        Listing existing = listingRepository.findBySellerIdAndCreateIdempotencyKey(sellerId, idempotencyKey)
                .orElse(null);
        if (existing == null) {
            return Optional.empty();
        }
        if (sameRequest(existing, request)) {
            return Optional.of(ListingResponse.from(existing));
        }
        throw new IdempotencyKeyReusedException();
    }

    private static boolean sameRequest(Listing existing, CreateListingRequest request) {
        return existing.getProductId().equals(new ProductId(request.productId()))
                && existing.getProductName().equals(request.productName().trim())
                && ListingRequests.sameAmount(existing.getOfferedQuantity(), request.offeredQuantity())
                && ListingRequests.sameAmount(existing.getMinOrderQuantity(), request.minOrderQuantity())
                && ListingRequests.sameAmount(existing.getAskingUnitPrice(), request.askingUnitPriceValue())
                && ListingRequests.samePlotIds(
                        existing.getPlots().stream().map(p -> p.sourceRegistryEntryId().value()).toList(),
                        request.registryEntryIds())
                && ListingRequests.sameDeclaredGoods(existing.getGoods(), request);
    }

    // Phase 2 creates coffee listings only (design §1/§9, matrix 22): the product's category must be approved as COFFEE.
    private Category coffeeCategoryOf(Product product) {
        Category category = product.getCategoryId() == null ? null
                : categoryRepository.findById(new CategoryId(product.getCategoryId())).orElse(null);
        if (category == null || !category.isApprovedCoffee()) {
            throw new IllegalArgumentException("Phase 2 accepts COFFEE listings only: product "
                    + product.getProductId().value() + " is not in an approved coffee category");
        }
        return category;
    }
}
