package com.agricontract.product.application.dto;

import com.agricontract.product.common.PaginatedRequest;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ListingSearchRequest extends PaginatedRequest {
    /** Keep a listing only when its seller's public reputation score is at least this; unrated sellers pass. */
    @Min(0) @Max(100)
    private Integer minSellerScore;
}
