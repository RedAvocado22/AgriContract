package com.agricontract.product.application.usecase;

import com.agricontract.product.application.dto.CreateListingRequest;
import com.agricontract.product.application.dto.ListingResponse;
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
                samePlots(existing, request.registryEntryIds()) &&
                sameDeclaredGoods(existing.getGoods(), request)) {
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
