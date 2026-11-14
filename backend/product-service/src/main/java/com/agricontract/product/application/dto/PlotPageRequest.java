package com.agricontract.product.application.dto;

import com.agricontract.product.domain.repository.PageQuery;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/** listMyPlots paging. The order is fixed (newest import first), so unlike PaginatedRequest there is no sort. */
@Data
public class PlotPageRequest {

    @Min(0) @Max(10000)
    private Integer page = 0;

    @Min(1) @Max(100)
    private Integer size = 20;

    public PageQuery toPageQuery() {
        return PageQuery.of(page != null ? page : 0, size != null ? size : 20);
    }
}
