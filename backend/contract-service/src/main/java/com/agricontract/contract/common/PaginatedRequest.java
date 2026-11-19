package com.agricontract.contract.common;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.agricontract.contract.domain.repository.PageQuery;

@Data
@NoArgsConstructor
public abstract class PaginatedRequest {

    @Min(0) @Max(10000)
    private Integer page = 0;

    @Min(1) @Max(100)
    private Integer size = 20;

    private String sortBy;

    @Pattern(regexp = "^(ASC|DESC)$", flags = Pattern.Flag.CASE_INSENSITIVE,
            message = "Sort direction must be ASC or DESC")
    private String sortDirection = "ASC";

    public PageQuery toPageQuery() {
        int p = page != null ? page : 0;
        int s = size != null ? size : 20;
        String sort = sortBy != null && !sortBy.isBlank() ? sortBy : null;
        return new PageQuery(p, s, sort, sort != null && "DESC".equalsIgnoreCase(sortDirection));
    }
}
