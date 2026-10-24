package com.agricontract.product.application.usecase;

import com.agricontract.product.application.dto.CreateListingRequest;
import com.agricontract.product.application.dto.ListingResponse;
import com.agricontract.product.common.exception.PlotNotEligibleException;
import com.agricontract.product.common.exception.PlotNotOwnedException;
import com.agricontract.product.common.exception.ProductNotFoundException;
import com.agricontract.product.domain.model.Listing;
import com.agricontract.product.domain.model.PlotRegistryEntry;
import com.agricontract.product.domain.model.Product;
import com.agricontract.product.domain.model.vo.ListingId;
import com.agricontract.product.domain.model.vo.ListingPlot;
import com.agricontract.product.domain.model.vo.Money;
import com.agricontract.product.domain.model.vo.ProductId;
import com.agricontract.product.domain.model.vo.Quantity;
import com.agricontract.product.domain.model.vo.RegistryEntryId;
import com.agricontract.product.domain.repository.ListingRepository;
import com.agricontract.product.domain.repository.PlotRegistryRepository;
import com.agricontract.product.domain.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CreateListingUseCaseImpl implements CreateListingUseCase {

    private final ListingRepository listingRepository;
    private final ProductRepository productRepository;
    private final PlotRegistryRepository plotRegistryRepository;

    @Override
    public ListingResponse execute(String sellerId, CreateListingRequest request) {
        ListingId listingId = (request.listingId() != null && !request.listingId().isBlank())
                ? new ListingId(request.listingId())
                : new ListingId(UUID.randomUUID().toString());

        Optional<ListingResponse> matched = assertSameRequestOrConflict(listingId, sellerId, request);
        if (matched.isPresent()) {
            return matched.get();
        }

        Product product = productRepository.findById(new ProductId(request.productId()))
                .orElseThrow(() -> new ProductNotFoundException(request.productId()));
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
                plots
        );

        Listing saved;
        try {
            saved = listingRepository.save(listing);
        } catch (DataIntegrityViolationException e) {
            return assertSameRequestOrConflict(listingId, sellerId, request).orElseThrow(() -> e);
        }

        return toResponse(saved);
    }

    private Optional<ListingResponse> assertSameRequestOrConflict(ListingId listingId, String sellerId, CreateListingRequest request) {
        Listing existing = listingRepository.findById(listingId).orElse(null);
        if (existing == null) {
            return Optional.empty();
        }
        if (existing.getSellerId().equals(sellerId) &&
                existing.getProductId().equals(new ProductId(request.productId())) &&
                existing.getQuantity().equals(new Quantity(request.quantity(), request.quantityUnit())) &&
                existing.getPriceFloor().equals(new Money(request.priceFloor(), request.currency())) &&
                existing.getDeliveryDeadline().equals(request.deliveryDeadline()) &&
                sameQuantity(existing.getMinOrderQuantity(), request.minOrderQuantity()) &&
                samePlots(existing, request.registryEntryIds())) {
            return Optional.of(toResponse(existing));
        }
        throw new IllegalStateException("Listing " + listingId.value() + " already exists with different attributes");
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
