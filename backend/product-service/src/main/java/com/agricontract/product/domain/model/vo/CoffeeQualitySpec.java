package com.agricontract.product.domain.model.vo;

import java.math.BigDecimal;

/**
 * Seller-declared coffee quality (contract CoffeeQualitySpec, design §8b). {@code type} is the field an inspector
 * can match exactly; the three percentages are measured.
 */
public record CoffeeQualitySpec(CoffeeType type, BigDecimal moisturePercent, BigDecimal foreignMatterPercent,
                                BigDecimal blackBrokenBeansPercent) {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    // Stored as DECIMAL(5,2): normalized to scale 2 so 12.5 and 12.50 are the same declared value.
    public CoffeeQualitySpec {
        if (type == null) throw new IllegalArgumentException("Coffee type must not be null");
        moisturePercent = percent(moisturePercent, "moisturePercent");
        foreignMatterPercent = percent(foreignMatterPercent, "foreignMatterPercent");
        blackBrokenBeansPercent = percent(blackBrokenBeansPercent, "blackBrokenBeansPercent");
    }

    private static BigDecimal percent(BigDecimal value, String field) {
        if (value == null || value.signum() < 0 || value.compareTo(HUNDRED) > 0) {
            throw new IllegalArgumentException(field + " must be between 0 and 100");
        }
        // never round a declared value silently
        if (value.stripTrailingZeros().scale() > 2) {
            throw new IllegalArgumentException(field + " allows at most 2 decimals");
        }
        return value.setScale(2);
    }
}
