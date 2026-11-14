package com.agricontract.product.application.usecase;

import com.agricontract.product.application.dto.CreateListingRequest;
import com.agricontract.product.application.dto.CreatedListing;
import com.agricontract.product.application.dto.ListingResponse;
import com.agricontract.product.application.port.UserEligibilityPort;
import com.agricontract.product.common.exception.ActorNotEligibleException;
import com.agricontract.product.common.exception.IdempotencyKeyReusedException;
import com.agricontract.product.common.exception.PlotNotEligibleException;
import com.agricontract.product.common.exception.PlotNotOwnedException;
import com.agricontract.product.common.exception.ProductNotFoundException;
import com.agricontract.product.domain.model.Category;
import com.agricontract.product.domain.model.Listing;
import com.agricontract.product.domain.model.PlotRegistryEntry;
import com.agricontract.product.domain.model.Product;
import com.agricontract.product.domain.model.vo.CategoryId;
import com.agricontract.product.domain.model.vo.CoffeeQualitySpec;
import com.agricontract.product.domain.model.vo.ListingGoods;
import com.agricontract.product.domain.model.vo.ListingId;
import com.agricontract.product.domain.model.vo.ListingPlot;
import com.agricontract.product.domain.model.vo.Money;
import com.agricontract.product.domain.model.vo.ProductId;
import com.agricontract.product.domain.model.vo.Quantity;
import com.agricontract.product.domain.model.vo.RegistryEntryId;
import com.agricontract.product.domain.repository.CategoryRepository;
import com.agricontract.product.domain.repository.ListingRepository;
import com.agricontract.product.domain.repository.PlotRegistryRepository;
import com.agricontract.product.domain.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CreateListingUseCaseImpl implements CreateListingUseCase {

    private final ListingRepository listingRepository;
    private final ProductRepository productRepository;
    private final PlotRegistryRepository plotRegistryRepository;
    private final CategoryRepository categoryRepository;
    private final UserEligibilityPort userEligibilityPort;
    private final Clock clock;

    static final int MAX_IDEMPOTENCY_KEY_LENGTH = 255;

    @Override
    public CreatedListing execute(String sellerId, String idempotencyKey, CreateListingRequest request) {
        requireIdempotencyKey(idempotencyKey);
        assertSellerEligible(sellerId);

        Optional<ListingResponse> replayed = replayOf(sellerId, idempotencyKey, request);
        if (replayed.isPresent()) {
            return new CreatedListing(replayed.get(), false);
        }

        ListingId listingId = (request.listingId() != null && !request.listingId().isBlank())
                ? new ListingId(request.listingId())
                : new ListingId(UUID.randomUUID().toString());

        Optional<ListingResponse> matched = assertSameRequestOrConflict(listingId, sellerId, request);
        if (matched.isPresent()) {
            return new CreatedListing(matched.get(), false);
        }

        Product product = productRepository.findById(new ProductId(request.productId()))
                .orElseThrow(() -> new ProductNotFoundException(request.productId()));
        Category category = coffeeCategoryOf(product);
        List<ListingPlot> plots = plotsFor(sellerId, request.registryEntryIds());

        Listing listing = Listing.create(
                listingId,
                sellerId,
                new ProductId(request.productId()),
                product.getName(),
                product.getCoverImageUrl(),
                new Quantity(request.quantity(), request.quantityUnit()),
                request.minOrderQuantity(),
                new Money(request.priceFloor(), request.currency()),
                request.deliveryDeadline(),
                plots,
                new ListingGoods(
                        category.getCategoryId(),
                        category.getCommodity(),
                        product.getCropYear(),
                        product.getHarvestSeason(),
                        product.getCertifications(),
                        request.regionText().trim(),
                        request.declaredQualitySpec().toDomain(),
                        blankToNull(request.packagingDeclaration()),
                        blankToNull(request.warehouseLocation()),
                        request.estimatedAvailabilityDate(),
                        request.traceabilityEvidencePackageId())
        );

        Listing saved;
        try {
            saved = listingRepository.save(listing, idempotencyKey);
        } catch (DataIntegrityViolationException e) {
            // a concurrent request with the same key (or the same listingId) committed first
            Optional<ListingResponse> winner = replayOf(sellerId, idempotencyKey, request)
                    .or(() -> assertSameRequestOrConflict(listingId, sellerId, request));
            return new CreatedListing(winner.orElseThrow(() -> e), false);
        }

        return new CreatedListing(toResponse(saved), true);
    }

    // @RequestHeader already turns a missing header into 400; a blank or oversized one is refused the same way.
    private static void requireIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("Idempotency-Key must not be blank");
        }
        if (idempotencyKey.length() > MAX_IDEMPOTENCY_KEY_LENGTH) {
            throw new IllegalArgumentException(
                    "Idempotency-Key must be at most " + MAX_IDEMPOTENCY_KEY_LENGTH + " characters");
        }
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
        boolean sameListingId = request.listingId() == null || request.listingId().isBlank()
                || existing.getListingId().value().equals(request.listingId());
        if (sameListingId && sameRequest(existing, sellerId, request)) {
            return Optional.of(toResponse(existing));
        }
        throw new IdempotencyKeyReusedException();
    }

    /**
     * Fail-closed gate (user-service design §3, matrix 21f), before any read or write: a user-service outage (503)
     * or an ineligible seller (403) never creates a listing. Replays are gated too, so no write path skips the check.
     */
    private void assertSellerEligible(String sellerId) {
        boolean eligible = userEligibilityPort.load(sellerId)
                .map(facts -> facts.allowsTradingAt(clock.instant()))
                .orElse(false);
        if (!eligible) {
            throw new ActorNotEligibleException();
        }
    }

    private Optional<ListingResponse> assertSameRequestOrConflict(ListingId listingId, String sellerId, CreateListingRequest request) {
        Listing existing = listingRepository.findById(listingId).orElse(null);
        if (existing == null) {
            return Optional.empty();
        }
        if (sameRequest(existing, sellerId, request)) {
            return Optional.of(toResponse(existing));
        }
        throw new IllegalStateException("Listing " + listingId.value() + " already exists with different attributes");
    }

    private static boolean sameRequest(Listing existing, String sellerId, CreateListingRequest request) {
        return existing.getSellerId().equals(sellerId) &&
                existing.getProductId().equals(new ProductId(request.productId())) &&
                existing.getQuantity().equals(new Quantity(request.quantity(), request.quantityUnit())) &&
                existing.getPriceFloor().equals(new Money(request.priceFloor(), request.currency())) &&
                existing.getDeliveryDeadline().equals(request.deliveryDeadline()) &&
                sameQuantity(existing.getMinOrderQuantity(), request.minOrderQuantity()) &&
                samePlots(existing, request.registryEntryIds()) &&
                sameDeclaredGoods(existing.getGoods(), request);
    }

    /**
     * Copies the chosen registry plots (design §2.4). Every id must be the seller's own plot and past the PENDING
     * geo check; the listing keeps these copies even if the registry entry is re-imported later.
     */
    private List<ListingPlot> plotsFor(String sellerId, List<String> registryEntryIds) {
        Set<RegistryEntryId> ids = new LinkedHashSet<>();
        for (String id : registryEntryIds) {
            if (!ids.add(new RegistryEntryId(id))) {
                throw new IllegalArgumentException("Plot " + id + " is listed more than once");
            }
        }
        Map<RegistryEntryId, PlotRegistryEntry> found = new HashMap<>();
        for (PlotRegistryEntry entry : plotRegistryRepository.findAllById(ids)) {
            found.put(entry.getRegistryEntryId(), entry);
        }

        List<ListingPlot> plots = new ArrayList<>(ids.size());
        for (RegistryEntryId id : ids) {
            PlotRegistryEntry entry = found.get(id);
            if (entry == null || !entry.isOwnedBy(sellerId)) {
                throw new PlotNotOwnedException(id.value());
            }
            if (!entry.canBackListing()) {
                throw new PlotNotEligibleException(id.value());
            }
            plots.add(new ListingPlot(entry.getRegistryEntryId(), entry.getHouseholdLabel(), entry.getGeometry(),
                    entry.getVerificationLevel()));
        }
        return plots;
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

    // the seller-declared part of the goods; the product-derived part is not in the request
    private static boolean sameDeclaredGoods(ListingGoods goods, CreateListingRequest request) {
        CoffeeQualitySpec spec = goods.declaredQualitySpec();
        return spec != null
                && spec.equals(request.declaredQualitySpec().toDomain())
                && Objects.equals(goods.regionText(), request.regionText().trim())
                && Objects.equals(goods.packagingDeclaration(), blankToNull(request.packagingDeclaration()))
                && Objects.equals(goods.warehouseLocation(), blankToNull(request.warehouseLocation()))
                && Objects.equals(goods.estimatedAvailabilityDate(), request.estimatedAvailabilityDate())
                && Objects.equals(goods.traceabilityEvidencePackageId(), request.traceabilityEvidencePackageId());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static boolean samePlots(Listing existing, List<String> registryEntryIds) {
        Set<String> stored = new HashSet<>();
        existing.getPlots().forEach(plot -> stored.add(plot.sourceRegistryEntryId().value()));
        return stored.equals(new HashSet<>(registryEntryIds)) && stored.size() == registryEntryIds.size();
    }

    private static boolean sameQuantity(BigDecimal a, BigDecimal b) {
        return a == null ? b == null : b != null && a.compareTo(b) == 0;
    }

    private ListingResponse toResponse(Listing listing) {
        return ListingResponse.from(listing);
    }
}
