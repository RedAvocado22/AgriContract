package com.agricontract.product.application.dto;

import com.agricontract.product.domain.model.Category;
import com.agricontract.product.domain.model.vo.Commodity;
import com.fasterxml.jackson.annotation.JsonInclude;

/** listApprovedCategories item. commodity is null for a legacy category the Operator has not mapped yet. */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record CategorySummary(String categoryId, String name, Commodity commodity) {

    public static CategorySummary from(Category category) {
        return new CategorySummary(category.getCategoryId().value(), category.getName(), category.getCommodity());
    }
}
