package com.agricontract.product.common;

import com.agricontract.product.application.dto.PagedResult;
import org.springframework.data.domain.Pageable;

import java.util.List;

public record PaginatedResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last,
        boolean empty
) {
    public static <T> PaginatedResponse<T> from(PagedResult<T> result, Pageable pageable) {
        int pageNum = pageable.getPageNumber();
        boolean isLast = result.totalPages() == 0 || pageNum >= result.totalPages() - 1;
        return new PaginatedResponse<>(
                result.content(),
                pageNum,
                pageable.getPageSize(),
                result.totalElements(),
                result.totalPages(),
                pageNum == 0,
                isLast,
                result.content().isEmpty()
        );
    }
}
