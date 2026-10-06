package com.agricontract.escrow.common;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

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

    public Pageable toPageable() {
        int p = page != null ? page : 0;
        int s = size != null ? size : 20;
        if (sortBy != null && !sortBy.isBlank()) {
            Sort.Direction dir = "DESC".equalsIgnoreCase(sortDirection)
                    ? Sort.Direction.DESC : Sort.Direction.ASC;
            return PageRequest.of(p, s, Sort.by(dir, sortBy));
        }
        return PageRequest.of(p, s);
    }
}
