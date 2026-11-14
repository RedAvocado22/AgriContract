package com.agricontract.product.application.usecase;

import com.agricontract.product.application.dto.CreateListingRequest;
import com.agricontract.product.domain.model.vo.CoffeeQualitySpec;
import com.agricontract.product.domain.model.vo.ListingGoods;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/** Helpers shared by createListing and updateListing, which take the same body. */
final class ListingRequests {

    static final int MAX_IDEMPOTENCY_KEY_LENGTH = 255;

    private ListingRequests() {
    }

    // @RequestHeader already turns a missing header into 400; a blank or oversized one is refused the same way.
    static void requireIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("Idempotency-Key must not be blank");
        }
        if (idempotencyKey.length() > MAX_IDEMPOTENCY_KEY_LENGTH) {
            throw new IllegalArgumentException(
                    "Idempotency-Key must be at most " + MAX_IDEMPOTENCY_KEY_LENGTH + " characters");
        }
    }

    /**
     * Listing goods from the request's seller-declared fields; category, commodity and season data are kept from
     * {@code derived} (the product at create, the listing's own copy at edit).
     */
    static ListingGoods goods(ListingGoods derived, CreateListingRequest request) {
        return new ListingGoods(
                derived.categoryId(),
                derived.commodity(),
                derived.cropYear(),
                derived.harvestSeason(),
                derived.certifications(),
                request.regionText().trim(),
                request.declaredQualitySpec().toDomain(),
                blankToNull(request.packagingDeclaration()),
                blankToNull(request.warehouseLocation()),
                request.estimatedAvailabilityDate(),
                request.traceabilityEvidencePackageId());
    }

    // the seller-declared part of the goods; the product-derived part is not in the request
    static boolean sameDeclaredGoods(ListingGoods goods, CreateListingRequest request) {
        CoffeeQualitySpec spec = goods.declaredQualitySpec();
        return spec != null
                && spec.equals(request.declaredQualitySpec().toDomain())
                && Objects.equals(goods.regionText(), request.regionText().trim())
                && Objects.equals(goods.packagingDeclaration(), blankToNull(request.packagingDeclaration()))
                && Objects.equals(goods.warehouseLocation(), blankToNull(request.warehouseLocation()))
                && Objects.equals(goods.estimatedAvailabilityDate(), request.estimatedAvailabilityDate())
                && Objects.equals(goods.traceabilityEvidencePackageId(), request.traceabilityEvidencePackageId());
    }

    static boolean samePlotIds(List<String> stored, List<String> requested) {
        return new HashSet<>(stored).equals(new HashSet<>(requested)) && stored.size() == requested.size();
    }

    static boolean sameAmount(BigDecimal a, BigDecimal b) {
        return a == null ? b == null : b != null && a.compareTo(b) == 0;
    }

    // productName is a snapshot the server copies (design §4); the client's copy must agree with it
    static void requireSameProductName(String expected, CreateListingRequest request) {
        if (!expected.equals(request.productName().trim())) {
            throw new IllegalArgumentException("productName does not match the product's name: " + expected);
        }
    }

    /**
     * SHA-256 of the request as the server reads it (amounts by value, plots as a set, blank text as absent), so a
     * replay of the same edit matches and any change does not.
     */
    static String fingerprint(CreateListingRequest request) {
        CoffeeQualitySpec spec = request.declaredQualitySpec().toDomain();
        StringBuilder canonical = new StringBuilder();
        Stream.of(
                request.productId(),
                request.productName().trim(),
                plain(request.offeredQuantity()),
                plain(request.minOrderQuantity()),
                plain(request.askingUnitPriceValue()),
                String.join(",", request.registryEntryIds().stream().sorted().toList()),
                request.regionText().trim(),
                spec.type().name(),
                plain(spec.moisturePercent()),
                plain(spec.foreignMatterPercent()),
                plain(spec.blackBrokenBeansPercent()),
                blankToNull(request.packagingDeclaration()),
                blankToNull(request.warehouseLocation()),
                request.estimatedAvailabilityDate() == null ? null : request.estimatedAvailabilityDate().toString(),
                request.traceabilityEvidencePackageId()
        ).forEach(part -> // length-prefixed, so free text cannot shift a value into the next field
                canonical.append(part == null ? "-" : part.length() + ":" + part).append(';'));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private static String plain(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
