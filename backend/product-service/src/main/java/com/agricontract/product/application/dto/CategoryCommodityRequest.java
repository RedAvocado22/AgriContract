package com.agricontract.product.application.dto;

import com.agricontract.product.domain.model.vo.Commodity;
import jakarta.validation.constraints.NotNull;

// The commodity the Operator maps a category to (design §9), on approval or for a category approved before V7.
public record CategoryCommodityRequest(
        @NotNull Commodity commodity
) {}
