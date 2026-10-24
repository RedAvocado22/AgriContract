package com.agricontract.product.application.dto;

import com.agricontract.product.domain.model.vo.CoffeeQualitySpec;
import com.agricontract.product.domain.model.vo.CoffeeType;
import com.agricontract.product.domain.model.vo.Commodity;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

// Contract CoffeeQualitySpec: commodity is the variant tag and can only be COFFEE.
public record CoffeeQualitySpecDto(
        @NotNull Commodity commodity,
        @NotNull CoffeeType type,
        @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal moisturePercent,
        @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal foreignMatterPercent,
        @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal blackBrokenBeansPercent
) {
    // additionalProperties: false in the contract
    @JsonAnySetter
    void rejectUnknownProperty(String name, Object value) {
        throw new IllegalArgumentException("Unknown property in declaredQualitySpec: " + name);
    }

    public CoffeeQualitySpec toDomain() {
        return new CoffeeQualitySpec(type, moisturePercent, foreignMatterPercent, blackBrokenBeansPercent);
    }

    public static CoffeeQualitySpecDto from(CoffeeQualitySpec spec) {
        return spec == null ? null : new CoffeeQualitySpecDto(Commodity.COFFEE, spec.type(), spec.moisturePercent(),
                spec.foreignMatterPercent(), spec.blackBrokenBeansPercent());
    }
}
